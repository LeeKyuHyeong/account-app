package com.kyuhyeong.account.api.receipt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyuhyeong.account.ai.model.MerchantHistoryContext;
import com.kyuhyeong.account.ai.model.ReceiptAnalysisResult;
import com.kyuhyeong.account.ai.service.MerchantHistoryProvider;
import com.kyuhyeong.account.ai.service.ReceiptAnalysisService;
import com.kyuhyeong.account.api.transaction.TransactionHistoryService;
import com.kyuhyeong.account.core.entity.Category;
import com.kyuhyeong.account.core.entity.Household;
import com.kyuhyeong.account.core.entity.Receipt;
import com.kyuhyeong.account.core.entity.Transaction;
import com.kyuhyeong.account.core.entity.User;
import com.kyuhyeong.account.core.enums.CategoryType;
import com.kyuhyeong.account.core.enums.PlanType;
import com.kyuhyeong.account.core.enums.TransactionStatus;
import com.kyuhyeong.account.core.repository.CategoryRepository;
import com.kyuhyeong.account.core.repository.HouseholdRepository;
import com.kyuhyeong.account.core.repository.ReceiptRepository;
import com.kyuhyeong.account.core.repository.TransactionRepository;
import com.kyuhyeong.account.core.repository.UserRepository;
import com.kyuhyeong.account.core.tenant.HouseholdContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * 영수증 업로드 → AI 분석 → Receipt + DRAFT Transaction 자동 생성 (Task 6 본체).
 *
 * <p>흐름 ({@code @Transactional}):
 * <ol>
 *   <li>이미지 바이트 → {@link ReceiptStorage} 로 디스크 저장 (path 획득)</li>
 *   <li>{@link Receipt} insert (이미지 메타)</li>
 *   <li>{@link MerchantHistoryProvider} 로 가구 학습 이력 조회</li>
 *   <li>{@link ReceiptAnalysisService#analyze} 로 Claude 분석</li>
 *   <li>Receipt 에 OCR raw JSON 갱신, processed_at 세팅</li>
 *   <li>분석된 카테고리명 → 가구 카테고리 매칭 (fallback: 기타 변동 → 첫 VARIABLE → 첫 카테고리)</li>
 *   <li>{@link Transaction} insert (DRAFT, confidence/occurred_at/merchant 포함)</li>
 * </ol>
 *
 * <p>가구 격리는 {@link HouseholdContext} + Hibernate {@code householdFilter} 가 자동
 * 적용 — 본 서비스는 {@code householdId} 를 명시 전달받지 않고 ctx 에서 조회한다 (JWT
 * 필터가 set 한 값이 단일 진입점).
 */
@Service
@RequiredArgsConstructor
public class ReceiptIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptIngestionService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ReceiptStorage receiptStorage;
    private final ReceiptAnalysisService analysisService;
    private final MerchantHistoryProvider historyProvider;
    private final ReceiptRepository receiptRepository;
    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final HouseholdRepository householdRepository;
    private final ObjectMapper objectMapper;
    private final TransactionHistoryService historyService;

    @Transactional
    public IngestResult ingest(MultipartFile image, Long uploaderUserId) throws IOException {
        Long householdId = HouseholdContext.get();

        // 0) 구독 한도 게이팅 — 디스크 저장·Claude 호출 전 fail-fast (한도 초과 시 비용 미발생).
        //    Household 는 비격리 엔티티지만 ctx 의 신뢰된 가구 ID 만 사용하므로 누수 없음.
        Household household = householdRepository.getReferenceById(householdId);
        int quota = household.getPlanType().monthlyReceiptQuota();
        if (quota != PlanType.UNLIMITED) {
            LocalDateTime monthStart = LocalDate.now(KST).withDayOfMonth(1).atStartOfDay();
            long usedThisMonth = receiptRepository.countByCreatedAtGreaterThanEqual(monthStart);
            if (usedThisMonth >= quota) {
                throw new ReceiptQuotaExceededException(quota);
            }
        }

        byte[] bytes = image.getBytes();
        String contentType = image.getContentType();

        // 1) 디스크 저장 — 분석 실패 시에도 원본은 보존 (재시도 용이)
        String imagePath = receiptStorage.store(householdId, contentType, bytes);

        // 2) Receipt insert (분석 전 상태)
        User uploader = userRepository.getReferenceById(uploaderUserId);
        Receipt receipt = Receipt.builder()
                .household(household)
                .uploader(uploader)
                .imagePath(imagePath)
                .originalFilename(image.getOriginalFilename() == null ? "unknown" : image.getOriginalFilename())
                .fileSize(image.getSize())
                .build();
        receipt = receiptRepository.save(receipt);

        // 3-4) 학습 이력 + Claude 분석
        MerchantHistoryContext history = historyProvider.getRecentHistory(
                householdId, MerchantHistoryContext.DEFAULT_MAX_ENTRIES);
        ReceiptAnalysisResult result = analysisService.analyze(bytes, contentType, history);

        // 5) Receipt.processed_at + raw JSON
        receipt.markProcessed(serialize(result));

        // 6) 카테고리 매칭
        Category category = resolveCategory(result.category());

        // 7) DRAFT Transaction insert
        Transaction tx = Transaction.builder()
                .household(household)
                .author(uploader)
                .category(category)
                .amount(result.total() == null ? BigDecimal.ZERO : result.total())
                .occurredAt(resolveOccurredAt(result.date(), result.time()))
                .merchant(result.merchant())
                .paymentMethod(result.paymentMethod())
                .memo(null)
                .receipt(receipt)
                .confidence(BigDecimal.valueOf(result.confidence())
                        .setScale(3, RoundingMode.HALF_UP))
                .status(TransactionStatus.DRAFT)
                .build();
        tx = transactionRepository.save(tx);
        historyService.logCreate(tx, uploaderUserId);

        log.info("Receipt ingested: receiptId={}, transactionId={}, merchant='{}', total={}, confidence={}",
                receipt.getId(), tx.getId(), result.merchant(), result.total(), result.confidence());

        return new IngestResult(receipt.getId(), tx.getId(), result);
    }

    /**
     * 분석된 일자/시각을 거래 발생 시각으로 조합한다.
     *
     * <ul>
     *   <li>일자 없음 → 분석 시점의 현재 시각 (KST)</li>
     *   <li>일자 + 시각 → 영수증에 찍힌 시각 그대로</li>
     *   <li>일자만 (시각 못 읽음) → 그 날짜 + 분석 시점의 현재 시각</li>
     * </ul>
     */
    private LocalDateTime resolveOccurredAt(LocalDate date, LocalTime time) {
        if (date == null) {
            return LocalDateTime.now(KST);
        }
        return date.atTime(time != null ? time : LocalTime.now(KST));
    }

    private String serialize(ReceiptAnalysisResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.warn("Failed to re-serialize analysis result; storing null raw JSON", e);
            return null;
        }
    }

    /**
     * 분석된 카테고리명을 현재 가구의 카테고리 엔티티로 매핑.
     *
     * <p>매칭 우선순위 (모두 가구 격리 자동 적용):
     * <ol>
     *   <li>정확히 일치하는 카테고리</li>
     *   <li>"기타 변동" 으로 fallback (우리집 시드에 존재)</li>
     *   <li>VARIABLE 타입 첫 카테고리 (sort_order 순)</li>
     *   <li>아무 카테고리 (가구에 최소 1개는 존재해야 함)</li>
     * </ol>
     */
    private Category resolveCategory(String categoryName) {
        List<Category> categories = categoryRepository.findAll();
        if (categories.isEmpty()) {
            throw new IllegalStateException(
                    "Household has no categories — cannot create DRAFT transaction");
        }
        if (categoryName != null && !categoryName.isBlank()) {
            for (Category c : categories) {
                if (categoryName.equals(c.getName())) {
                    return c;
                }
            }
        }
        for (Category c : categories) {
            if ("기타 변동".equals(c.getName())) {
                return c;
            }
        }
        return categories.stream()
                .filter(c -> c.getType() == CategoryType.VARIABLE)
                .min(Comparator.comparingInt(Category::getSortOrder))
                .orElse(categories.get(0));
    }

    public record IngestResult(Long receiptId, Long transactionId, ReceiptAnalysisResult analysis) {}
}

package com.kyuhyeong.account.api.web;

import com.kyuhyeong.account.api.security.CustomUserDetails;
import com.kyuhyeong.account.api.transaction.TransactionDtos.CreateTransactionRequest;
import com.kyuhyeong.account.api.transaction.TransactionDtos.PageResponse;
import com.kyuhyeong.account.api.transaction.TransactionDtos.TransactionResponse;
import com.kyuhyeong.account.api.transaction.TransactionDtos.UpdateTransactionRequest;
import com.kyuhyeong.account.api.transaction.TransactionService;
import com.kyuhyeong.account.api.transaction.TransactionService.TransactionListQuery;
import com.kyuhyeong.account.core.enums.CategoryType;
import com.kyuhyeong.account.core.enums.TransactionStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 거래 SSR 화면 — 목록 / 입력 / 수정.
 *
 * <p>비즈니스 로직은 {@link TransactionService} 를 그대로 재사용한다. 세션 principal 에서
 * userId 는 {@link CustomUserDetails#getUserId()} 로 꺼낸다 (JWT 경로의 Long principal 과 다름).
 *
 * <p>폼 검증: 입력값을 record DTO 로 @ModelAttribute 바인딩 + @Valid → BindingResult.
 * 에러 시 제출 원본값(raw Map = {@code form}) 과 필드 에러(Map = {@code errors}) 를 모델에 담아
 * 폼을 재렌더한다 (th:field / setter 미사용 — DTO 는 record 유지).
 */
@Controller
@RequestMapping("/web/transactions")
@RequiredArgsConstructor
public class WebTransactionController {

    private static final DateTimeFormatter LOCAL_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final TransactionService transactionService;
    private final CategoryQueryService categoryQueryService;

    @GetMapping
    public String list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) CategoryType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            Model model) {

        PageResponse<TransactionResponse> result = transactionService.list(
                new TransactionListQuery(from, to, categoryId, type, status, page, size));

        // 날짜별 그룹핑 (occurred_at DESC 순서 유지)
        Map<LocalDate, List<TransactionResponse>> byDate = result.content().stream()
                .collect(Collectors.groupingBy(
                        t -> t.occurredAt().toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        // 페이지네이션 링크에 현재 필터를 보존하기 위한 query 조각
        StringBuilder fq = new StringBuilder();
        if (from != null) fq.append("&from=").append(from);
        if (to != null) fq.append("&to=").append(to);
        if (categoryId != null) fq.append("&categoryId=").append(categoryId);
        if (type != null) fq.append("&type=").append(type);
        if (status != null) fq.append("&status=").append(status);

        model.addAttribute("page", result);
        model.addAttribute("byDate", byDate);
        model.addAttribute("categories", categoryQueryService.findAllSorted());
        model.addAttribute("statuses", TransactionStatus.values());
        model.addAttribute("filterQuery", fq.toString());
        model.addAttribute("fFrom", from);
        model.addAttribute("fTo", to);
        model.addAttribute("fCategoryId", categoryId);
        model.addAttribute("fStatus", status);
        return "transactions/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", Map.of());
        model.addAttribute("errors", Map.of());
        model.addAttribute("defaultOccurredAt", LocalDateTime.now().format(LOCAL_DATETIME));
        model.addAttribute("categories", categoryQueryService.findAllSorted());
        return "transactions/new";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("dto") CreateTransactionRequest dto,
                         BindingResult binding,
                         @RequestParam Map<String, String> raw,
                         @AuthenticationPrincipal CustomUserDetails user,
                         Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("form", raw);
            model.addAttribute("errors", fieldErrors(binding));
            model.addAttribute("defaultOccurredAt", LocalDateTime.now().format(LOCAL_DATETIME));
            model.addAttribute("categories", categoryQueryService.findAllSorted());
            return "transactions/new";
        }
        transactionService.create(dto, user.getUserId());
        return "redirect:/web/transactions";
    }

    @GetMapping("/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("tx", transactionService.get(id));
        model.addAttribute("categories", categoryQueryService.findAllSorted());
        return "transactions/edit";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam Long categoryId,
                         @RequestParam(required = false) Boolean confirm,
                         @AuthenticationPrincipal CustomUserDetails user) {
        TransactionStatus status = Boolean.TRUE.equals(confirm) ? TransactionStatus.CONFIRMED : null;
        transactionService.update(id, new UpdateTransactionRequest(categoryId, status), user.getUserId());
        return "redirect:/web/transactions";
    }

    private static Map<String, String> fieldErrors(BindingResult binding) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fe : binding.getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return errors;
    }
}

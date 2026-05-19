package com.kyuhyeong.account.api.receipt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 영수증 이미지 디스크 저장.
 *
 * <p>경로 규약 (docs/account.md §6.1):
 * <pre>
 *   {storageRoot}/{householdId}/{yyyy}/{mm}/{uuid}.{ext}
 * </pre>
 *
 * <p>개발: {@code ./data/receipts/...} (프로젝트 루트 기준, gitignored)
 * <br>운영: {@code /mnt/data/receipts/...} (config 로 오버라이드)
 *
 * <p>본 클래스는 바이트 저장만 책임지며 압축/리사이즈는 클라이언트 측 책임이다
 * (Flutter 가 업로드 전 1280px 압축, §3.2).
 */
@Component
public class ReceiptStorage {

    private static final Map<String, String> EXT_BY_MIME = Map.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );

    private final Path storageRoot;

    public ReceiptStorage(@Value("${account.receipts.storage-root:./data/receipts}") String storageRoot) {
        this.storageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();
    }

    /**
     * 이미지 바이트를 디스크에 저장하고 절대 경로 문자열을 반환.
     *
     * @param householdId 가구 ID (디렉토리 분기)
     * @param contentType MIME 타입 (확장자 결정용); 매칭 안 되면 {@code bin} 으로 저장
     * @param bytes       이미지 바이트
     * @return 저장된 파일의 절대 경로 (DB {@code receipts.image_path} 에 그대로 저장)
     */
    public String store(Long householdId, String contentType, byte[] bytes) {
        String extension = EXT_BY_MIME.getOrDefault(
                contentType == null ? "" : contentType.toLowerCase(Locale.ROOT),
                "bin");

        LocalDate today = LocalDate.now();
        Path directory = storageRoot
                .resolve(String.valueOf(householdId))
                .resolve(String.format("%04d", today.getYear()))
                .resolve(String.format("%02d", today.getMonthValue()));

        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create receipt directory: " + directory, e);
        }

        Path target = directory.resolve(UUID.randomUUID() + "." + extension);
        try {
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write receipt file: " + target, e);
        }

        return target.toString();
    }
}

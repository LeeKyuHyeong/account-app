package com.kyuhyeong.account.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/**
 * 영수증 이미지 메타 + Claude OCR 원본 응답 (docs/account.md §6.1).
 *
 * <p>이미지 바이너리는 디스크 ({@code /mnt/data/receipts/{hid}/{yyyy}/{mm}/{uuid}.jpg}),
 * 본 엔티티는 경로 + 메타데이터만.
 */
@Entity
@Table(
        name = "receipts",
        indexes = @Index(name = "idx_receipts_hid_created", columnList = "household_id, created_at")
)
@Filter(name = "householdFilter", condition = "household_id = :currentHouseholdId")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Receipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false)
    private Household household;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User uploader;

    @Column(name = "image_path", nullable = false, length = 500)
    private String imagePath;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Lob
    @Column(name = "ocr_raw_json", columnDefinition = "LONGTEXT")
    private String ocrRawJson;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void markProcessed(String ocrRawJson) {
        this.ocrRawJson = ocrRawJson;
        this.processedAt = LocalDateTime.now();
    }
}

package com.kyuhyeong.account.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kyuhyeong.account.ai.config.ClaudeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * Claude Messages API의 Vision 기능을 호출하는 저수준 클라이언트.
 *
 * <p>책임은 단 하나: base64 이미지 + 프롬프트 텍스트 → API 호출 → 응답 텍스트 추출.
 * JSON 파싱, 비즈니스 로직(merchant_history 학습 등)은 호출자({@code ReceiptAnalysisService})의 몫.
 *
 * <p>본 클래스는 stateless 하며, Java 21 가상 스레드 + Spring RestClient 조합으로
 * 동시 다발적 영수증 업로드를 자연스럽게 처리한다.
 */
@Component
public class ClaudeVisionClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeVisionClient.class);
    private static final String MESSAGES_ENDPOINT = "/v1/messages";

    private final RestClient claudeRestClient;
    private final ClaudeProperties properties;

    public ClaudeVisionClient(RestClient claudeRestClient, ClaudeProperties properties) {
        this.claudeRestClient = claudeRestClient;
        this.properties = properties;
    }

    /**
     * 이미지 + 프롬프트를 Claude에 전달하고, assistant 메시지의 첫 텍스트 블록을 반환한다.
     *
     * @param base64Image   이미지의 base64 인코딩 (데이터 URL 프리픽스 없는 raw base64)
     * @param mediaType     이미지 MIME 타입 (image/jpeg, image/png, image/webp, image/gif)
     * @param prompt        분석 지시 프롬프트
     * @return Claude assistant의 응답 텍스트 (영수증 분석 JSON 문자열이어야 함)
     * @throws ClaudeApiException API 호출 실패 또는 응답 형식 이상 시
     */
    public String analyzeImage(String base64Image, String mediaType, String prompt) {
        if (base64Image == null || base64Image.isBlank()) {
            throw new IllegalArgumentException("base64Image must not be blank");
        }
        if (!isSupportedMediaType(mediaType)) {
            throw new IllegalArgumentException(
                    "Unsupported mediaType: " + mediaType
                            + " (supported: image/jpeg, image/png, image/webp, image/gif)");
        }

        MessageRequest request = buildRequest(base64Image, mediaType, prompt);

        log.debug("Calling Claude Vision API: model={}, imageBytes={}, promptLen={}",
                properties.model(), base64Image.length(), prompt.length());

        MessageResponse response;
        try {
            response = claudeRestClient.post()
                    .uri(MESSAGES_ENDPOINT)
                    .body(request)
                    .retrieve()
                    .body(MessageResponse.class);
        } catch (RestClientResponseException e) {
            log.error("Claude API error: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new ClaudeApiException(
                    "Claude API call failed: HTTP " + e.getStatusCode().value(), e);
        }

        if (response == null || response.content() == null || response.content().isEmpty()) {
            throw new ClaudeApiException("Claude returned empty response");
        }

        // assistant 메시지의 첫 텍스트 블록 추출
        String text = response.content().stream()
                .filter(block -> "text".equals(block.type()))
                .map(ContentBlock::text)
                .findFirst()
                .orElseThrow(() -> new ClaudeApiException(
                        "No text block in Claude response: " + response));

        log.debug("Claude response: stopReason={}, inputTokens={}, outputTokens={}",
                response.stopReason(),
                response.usage() != null ? response.usage().inputTokens() : -1,
                response.usage() != null ? response.usage().outputTokens() : -1);

        return text;
    }

    private MessageRequest buildRequest(String base64Image, String mediaType, String prompt) {
        ImageSource source = new ImageSource("base64", mediaType, base64Image);
        ContentBlockParam imageBlock = ContentBlockParam.image(source);
        ContentBlockParam textBlock = ContentBlockParam.text(prompt);

        UserMessage userMessage = new UserMessage("user", List.of(imageBlock, textBlock));

        return new MessageRequest(
                properties.model(),
                properties.maxTokens(),
                List.of(userMessage)
        );
    }

    private boolean isSupportedMediaType(String mediaType) {
        return mediaType != null && (
                mediaType.equals("image/jpeg")
                        || mediaType.equals("image/png")
                        || mediaType.equals("image/webp")
                        || mediaType.equals("image/gif"));
    }

    // ───────────────────────────────────────────────────────────
    // 요청/응답 DTO (Anthropic Messages API 스펙)
    // ───────────────────────────────────────────────────────────

    /** {@code POST /v1/messages} 요청 본문. */
    private record MessageRequest(
            @JsonProperty("model") String model,
            @JsonProperty("max_tokens") int maxTokens,
            @JsonProperty("messages") List<UserMessage> messages
    ) {}

    /** 메시지 한 건 (role + content). 이미지 분석은 보통 단일 user 메시지. */
    private record UserMessage(
            @JsonProperty("role") String role,
            @JsonProperty("content") List<ContentBlockParam> content
    ) {}

    /**
     * content 블록 (이미지 또는 텍스트). Anthropic은 두 타입을 모두 한 배열에 담는다.
     *
     * <p>image 블록은 source 만, text 블록은 text 만 보내야 한다. Jackson 기본값은 null 필드도
     * 직렬화하므로 — 과거 이 가정이 틀려 image 블록에 {@code "text":null} 이 섞여 Anthropic 이
     * 400 ("Extra inputs are not permitted") 으로 거부했다 — {@code @JsonInclude(NON_NULL)} 로
     * null 필드를 직렬화에서 제외한다. (package-private: 직렬화 회귀 테스트가 접근.)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ContentBlockParam(
            @JsonProperty("type") String type,
            @JsonProperty("source") ImageSource source,
            @JsonProperty("text") String text
    ) {
        static ContentBlockParam image(ImageSource source) {
            return new ContentBlockParam("image", source, null);
        }

        static ContentBlockParam text(String text) {
            return new ContentBlockParam("text", null, text);
        }
    }

    /** 이미지 소스 — base64 인코딩 방식만 사용 (URL 방식은 본 프로젝트에서 불필요). */
    record ImageSource(
            @JsonProperty("type") String type,
            @JsonProperty("media_type") String mediaType,
            @JsonProperty("data") String data
    ) {}

    /** {@code POST /v1/messages} 응답 본문. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MessageResponse(
            @JsonProperty("id") String id,
            @JsonProperty("role") String role,
            @JsonProperty("model") String model,
            @JsonProperty("content") List<ContentBlock> content,
            @JsonProperty("stop_reason") String stopReason,
            @JsonProperty("usage") Usage usage
    ) {}

    /** 응답 content 블록. 영수증 분석은 단일 text 블록만 반환됨. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContentBlock(
            @JsonProperty("type") String type,
            @JsonProperty("text") String text
    ) {}

    /** 토큰 사용량 — 비용 추적 및 monthly 집계용. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Usage(
            @JsonProperty("input_tokens") int inputTokens,
            @JsonProperty("output_tokens") int outputTokens
    ) {}

    /**
     * Claude API 호출 실패를 나타내는 예외.
     * <p>HTTP 4xx (잘못된 키, 이미지 너무 큼 등) / 5xx (Anthropic 측 오류) /
     * 응답 형식 이상 모두 이 예외로 통일하여 상위 계층에서 일관 처리.
     */
    public static class ClaudeApiException extends RuntimeException {
        public ClaudeApiException(String message) {
            super(message);
        }

        public ClaudeApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

package com.kyuhyeong.account.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Anthropic Messages API 의 content 블록 직렬화 회귀 테스트.
 *
 * <p>image 블록에 {@code "text":null} 이, text 블록에 {@code "source":null} 이 섞여 나가면
 * Anthropic 이 400 ("Extra inputs are not permitted") 으로 거부한다. {@code @JsonInclude(NON_NULL)}
 * 이 null 필드를 직렬화에서 제외하는지 검증한다.
 */
class ContentBlockSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void imageBlockOmitsNullTextField() throws Exception {
        var block = ClaudeVisionClient.ContentBlockParam.image(
                new ClaudeVisionClient.ImageSource("base64", "image/jpeg", "QUJD"));

        String json = objectMapper.writeValueAsString(block);

        assertThat(json).contains("\"type\":\"image\"");
        assertThat(json).contains("\"source\"");
        assertThat(json).doesNotContain("\"text\"");
    }

    @Test
    void textBlockOmitsNullSourceField() throws Exception {
        var block = ClaudeVisionClient.ContentBlockParam.text("분석해줘");

        String json = objectMapper.writeValueAsString(block);

        assertThat(json).contains("\"type\":\"text\"");
        assertThat(json).contains("\"text\":\"분석해줘\"");
        assertThat(json).doesNotContain("\"source\"");
    }
}

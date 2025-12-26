package me.hanju.parsekit.vlm;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import me.hanju.parsekit.IParser;
import me.hanju.parsekit.exception.ParseKitClientException;
import me.hanju.parsekit.exception.ParseKitException;
import me.hanju.parsekit.payload.PageContent;
import me.hanju.parsekit.payload.ParseResult;

/**
 * VLM (Vision Language Model) 클라이언트 구현체
 */
public final class VlmClient implements IParser {

  private static final Logger log = LoggerFactory.getLogger(VlmClient.class);

  private static final String OCR_PROMPT = """
      Extract all text from this image accurately.

      Rules:
      1. Convert tables to markdown table format to preserve structure
      2. Clearly distinguish titles, subtitles, and body text
      3. Use markdown list format for lists
      4. Maintain layout and hierarchy as much as possible
      5. Accurately recognize both Korean and English

      Output only the text, do not add explanations.""";

  private final WebClient webClient;
  private final String model;

  public VlmClient(final WebClient.Builder webClientBuilder, final String baseUrl, final String model) {
    if (webClientBuilder == null) {
      throw new IllegalArgumentException("webClientBuilder must not be null");
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("baseUrl must not be null or blank");
    }
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("model must not be null or blank");
    }

    // 큰 응답을 처리하기 위해 버퍼 크기 증가 (기본 256KB -> 16MB)
    final ExchangeStrategies strategies = ExchangeStrategies.builder()
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
        .build();

    this.webClient = webClientBuilder
        .baseUrl(baseUrl)
        .exchangeStrategies(strategies)
        .build();
    this.model = model;
    log.info("VLM 클라이언트 초기화: baseUrl={}, model={}", baseUrl, model);
  }

  @Override
  public String ocr(final byte[] imageBytes) {
    if (imageBytes == null || imageBytes.length == 0) {
      throw new IllegalArgumentException("imageBytes must not be null or empty");
    }

    final String base64Image = Base64.getEncoder().encodeToString(imageBytes);
    final String dataUrl = "data:image/png;base64," + base64Image;

    final Map<String, Object> payload = Map.of(
        "model", model,
        "messages", List.of(Map.of(
            "role", "user",
            "content", List.of(
                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)),
                Map.of("type", "text", "text", OCR_PROMPT)))),
        "max_tokens", 4096,
        "temperature", 0.0);

    final Map<String, Object> response;
    try {
      response = webClient.post()
          .uri("/v1/chat/completions")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(payload)
          .retrieve()
          .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
          })
          .block();
    } catch (final Exception e) {
      throw new ParseKitClientException("Request failed", e);
    }

    if (response == null) {
      throw new ParseKitException(406, "VLM: null response");
    }

    return extractContent(response);
  }

  @Override
  public ParseResult parse(final byte[] content, final String filename) {
    if (content == null || content.length == 0) {
      throw new IllegalArgumentException("content must not be null or empty");
    }
    if (filename == null || filename.isBlank()) {
      throw new IllegalArgumentException("filename must not be null or blank");
    }

    // 이미지인 경우 OCR 처리
    if (isImage(content)) {
      final String markdown = ocr(content);
      return new ParseResult(
          List.of(new PageContent(1, markdown)),
          Map.of());
    }

    // PDF의 경우 클라이언트에서 처리 불가 (PDF 라이브러리 필요)
    throw new ParseKitException(401, "VLM: PDF parsing not supported. Use DoclingClient for PDF files.");
  }

  @Override
  public boolean isAvailable() {
    try {
      // VLM 서버의 /health는 빈 응답(content-length: 0)을 반환하므로
      // 응답 상태 코드만 확인 (2xx면 성공)
      webClient.get()
          .uri("/health")
          .retrieve()
          .toBodilessEntity()
          .block();
      return true;
    } catch (final Exception e) {
      log.debug("VLM 서비스 연결 불가: {}", e.getMessage());
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  private String extractContent(final Map<String, Object> response) {
    final List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
    if (choices == null || choices.isEmpty()) {
      throw new ParseKitException(406, "VLM: empty choices");
    }

    final Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
    if (message == null) {
      throw new ParseKitException(406, "VLM: null message");
    }

    final String content = (String) message.get("content");
    if (content == null) {
      log.warn("VLM이 빈 content 반환");
      return "";
    }

    return content;
  }

  private boolean isImage(final byte[] content) {
    if (content.length < 8) {
      return false;
    }
    // PNG
    if (content[0] == (byte) 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G') {
      return true;
    }
    // JPEG
    if (content[0] == (byte) 0xFF && content[1] == (byte) 0xD8) {
      return true;
    }
    // GIF
    if (content[0] == 'G' && content[1] == 'I' && content[2] == 'F') {
      return true;
    }
    return false;
  }
}

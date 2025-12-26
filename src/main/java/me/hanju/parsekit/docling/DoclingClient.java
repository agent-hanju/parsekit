package me.hanju.parsekit.docling;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import me.hanju.parsekit.IParser;
import me.hanju.parsekit.exception.ParseKitClientException;
import me.hanju.parsekit.exception.ParseKitException;
import me.hanju.parsekit.payload.PageContent;
import me.hanju.parsekit.payload.ParseResult;

/**
 * Docling 클라이언트 구현체
 */
public final class DoclingClient implements IParser {

  private static final Logger log = LoggerFactory.getLogger(DoclingClient.class);

  private final WebClient webClient;

  public DoclingClient(final WebClient.Builder webClientBuilder, final String baseUrl) {
    if (webClientBuilder == null) {
      throw new IllegalArgumentException("webClientBuilder must not be null");
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("baseUrl must not be null or blank");
    }

    // 큰 응답을 처리하기 위해 버퍼 크기 증가 (기본 256KB -> 16MB)
    final ExchangeStrategies strategies = ExchangeStrategies.builder()
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
        .build();

    this.webClient = webClientBuilder
        .baseUrl(baseUrl)
        .exchangeStrategies(strategies)
        .build();
    log.info("Docling 클라이언트 초기화: baseUrl={}", baseUrl);
  }

  /**
   * 기본 설정으로 문서 파싱 (이미지 임베드 모드)
   */
  @Override
  public ParseResult parse(final byte[] content, final String filename) {
    return parse(content, filename, ImageExportMode.EMBEDDED);
  }

  /**
   * 이미지 내보내기 모드를 지정하여 문서 파싱
   *
   * @param content         문서 바이트 배열
   * @param filename        파일명
   * @param imageExportMode 이미지 내보내기 모드 (EMBEDDED: base64 인라인, PLACEHOLDER: 플레이스홀더 텍스트)
   */
  public ParseResult parse(final byte[] content, final String filename, final ImageExportMode imageExportMode) {
    if (content == null || content.length == 0) {
      throw new IllegalArgumentException("content must not be null or empty");
    }
    if (filename == null || filename.isBlank()) {
      throw new IllegalArgumentException("filename must not be null or blank");
    }
    if (imageExportMode == null) {
      throw new IllegalArgumentException("imageExportMode must not be null");
    }

    final Map<String, Object> response;
    try {
      response = webClient.post()
          .uri(uriBuilder -> uriBuilder
              .path("/v1/convert/file")
              .queryParam("image_export_mode", imageExportMode.getValue())
              .queryParam("to_formats", "md")
              .build())
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(BodyInserters.fromMultipartData(buildMultipartBody(content, filename)))
          .retrieve()
          .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
          })
          .block();
    } catch (final Exception e) {
      throw new ParseKitClientException("Request failed", e);
    }

    if (response == null) {
      throw new ParseKitException(306, "Docling: null response");
    }

    return parseResponse(response);
  }

  private org.springframework.util.MultiValueMap<String, org.springframework.http.HttpEntity<?>> buildMultipartBody(
      final byte[] content, final String filename) {
    final MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("files", content)
        .filename(filename)
        .contentType(MediaType.APPLICATION_OCTET_STREAM);
    return builder.build();
  }

  @Override
  public String ocr(final byte[] imageBytes) {
    if (imageBytes == null || imageBytes.length == 0) {
      throw new IllegalArgumentException("imageBytes must not be null or empty");
    }

    // Docling은 이미지도 문서로 처리 가능 (EasyOCR 내장)
    final ParseResult result = parse(imageBytes, "image.png");
    return result.asMarkdown();
  }

  @Override
  public boolean isAvailable() {
    try {
      final String health = webClient.get()
          .uri("/health")
          .retrieve()
          .bodyToMono(String.class)
          .block();
      return health != null;
    } catch (final Exception e) {
      log.debug("Docling 서비스 연결 불가: {}", e.getMessage());
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  private ParseResult parseResponse(final Map<String, Object> response) {
    // Docling 서버는 {document: {filename: ..., md_content: ...}} 형태로 응답
    final Map<String, Object> document = (Map<String, Object>) response.get("document");
    if (document == null) {
      throw new ParseKitException(302, "Docling: no document returned");
    }

    final String markdown = (String) document.get("md_content");
    if (markdown == null) {
      throw new ParseKitException(303, "Docling: null md_content");
    }

    final List<PageContent> pages = List.of(new PageContent(1, markdown));

    final Map<String, String> metadata = new java.util.HashMap<>();
    final String filename = (String) document.get("filename");
    if (filename != null) {
      metadata.put("filename", filename);
    }

    log.debug("Docling 파싱 완료: {} 페이지", pages.size());
    return new ParseResult(pages, metadata);
  }
}

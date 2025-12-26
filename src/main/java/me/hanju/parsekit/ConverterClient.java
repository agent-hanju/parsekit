package me.hanju.parsekit;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import me.hanju.parsekit.exception.ParseKitClientException;
import me.hanju.parsekit.exception.ParseKitException;
import me.hanju.parsekit.payload.ConvertResult;
import me.hanju.parsekit.payload.ImageConvertResult;
import me.hanju.parsekit.payload.ImagePage;

/**
 * 문서 변환 클라이언트 (LibreOffice 기반)
 */
public final class ConverterClient {

  private static final Logger log = LoggerFactory.getLogger(ConverterClient.class);

  private final WebClient webClient;

  public ConverterClient(final WebClient.Builder webClientBuilder, final String baseUrl) {
    if (webClientBuilder == null) {
      throw new IllegalArgumentException("webClientBuilder must not be null");
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("baseUrl must not be null or blank");
    }

    // 큰 응답을 처리하기 위해 버퍼 크기 증가 (기본 256KB -> 64MB)
    final ExchangeStrategies strategies = ExchangeStrategies.builder()
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(64 * 1024 * 1024))
        .build();

    this.webClient = webClientBuilder
        .baseUrl(baseUrl)
        .exchangeStrategies(strategies)
        .build();
    log.info("Converter 클라이언트 초기화: baseUrl={}", baseUrl);
  }

  public ConvertResult convert(final byte[] content, final String filename, final String contentType) {
    if (content == null || content.length == 0) {
      throw new IllegalArgumentException("content must not be null or empty");
    }
    if (filename == null || filename.isBlank()) {
      throw new IllegalArgumentException("filename must not be null or blank");
    }

    final MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("file", content)
        .filename(filename)
        .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM);

    final Map<String, Object> response;
    try {
      response = webClient.post()
          .uri("/convert")
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(BodyInserters.fromMultipartData(builder.build()))
          .retrieve()
          .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
          })
          .block();
    } catch (final Exception e) {
      throw new ParseKitClientException("Request failed", e);
    }

    if (response == null) {
      throw new ParseKitException(501, "ParseKit /convert: null response");
    }

    final int code = ((Number) response.getOrDefault("code", 0)).intValue();
    if (code != 0) {
      final String message = (String) response.getOrDefault("message", "Unknown error");
      throw new ParseKitException(code, message);
    }

    return parseConvertData(response.get("data"));
  }

  public byte[] convertRaw(final byte[] content, final String filename, final String contentType) {
    if (content == null || content.length == 0) {
      throw new IllegalArgumentException("content must not be null or empty");
    }
    if (filename == null || filename.isBlank()) {
      throw new IllegalArgumentException("filename must not be null or blank");
    }

    final MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("file", content)
        .filename(filename)
        .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM);

    final byte[] response;
    try {
      response = webClient.post()
          .uri("/convert/raw")
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(BodyInserters.fromMultipartData(builder.build()))
          .retrieve()
          .bodyToMono(byte[].class)
          .block();
    } catch (final Exception e) {
      throw new ParseKitClientException("Request failed", e);
    }

    if (response == null) {
      throw new ParseKitException(501, "ParseKit /convert/raw: null response");
    }

    log.debug("문서 변환 완료: {} bytes", response.length);
    return response;
  }

  /**
   * 문서를 이미지로 변환 (페이지별, NDJSON 스트리밍)
   *
   * @param content     파일 바이트 배열
   * @param filename    파일명
   * @param contentType MIME 타입 (null이면 application/octet-stream)
   * @param format      출력 포맷 (png, jpg, webp). null이면 png
   * @param dpi         해상도. 0 이하면 150
   * @return 페이지별 이미지 변환 결과
   */
  public ImageConvertResult convertToImages(final byte[] content, final String filename,
      final String contentType, final String format, final int dpi) {
    if (content == null || content.length == 0) {
      throw new IllegalArgumentException("content must not be null or empty");
    }
    if (filename == null || filename.isBlank()) {
      throw new IllegalArgumentException("filename must not be null or blank");
    }

    final String actualFormat = (format == null || format.isBlank()) ? "png" : format;
    final int actualDpi = (dpi <= 0) ? 150 : dpi;

    final MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("file", content)
        .filename(filename)
        .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM);

    final String responseBody;
    try {
      responseBody = webClient.post()
          .uri(uriBuilder -> uriBuilder
              .path("/convert/images")
              .queryParam("format", actualFormat)
              .queryParam("dpi", actualDpi)
              .build())
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(BodyInserters.fromMultipartData(builder.build()))
          .retrieve()
          .bodyToMono(String.class)
          .block();
    } catch (final Exception e) {
      throw new ParseKitClientException("Request failed", e);
    }

    if (responseBody == null || responseBody.isBlank()) {
      throw new ParseKitException(501, "ParseKit /convert/images: null response");
    }

    return parseNdjsonResponse(responseBody, actualFormat);
  }

  /**
   * 문서를 이미지로 변환하며 페이지별로 스트리밍 콜백 호출
   *
   * @param content     파일 바이트 배열
   * @param filename    파일명
   * @param contentType MIME 타입 (null이면 application/octet-stream)
   * @param format      출력 포맷 (png, jpg, webp). null이면 png
   * @param dpi         해상도. 0 이하면 150
   * @param pageHandler 페이지별 콜백 핸들러
   */
  public void convertToImagesStreaming(final byte[] content, final String filename,
      final String contentType, final String format, final int dpi,
      final Consumer<ImagePage> pageHandler) {
    if (content == null || content.length == 0) {
      throw new IllegalArgumentException("content must not be null or empty");
    }
    if (filename == null || filename.isBlank()) {
      throw new IllegalArgumentException("filename must not be null or blank");
    }
    if (pageHandler == null) {
      throw new IllegalArgumentException("pageHandler must not be null");
    }

    final String actualFormat = (format == null || format.isBlank()) ? "png" : format;
    final int actualDpi = (dpi <= 0) ? 150 : dpi;

    final MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("file", content)
        .filename(filename)
        .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM);

    final ObjectMapper objectMapper = new ObjectMapper();

    try {
      webClient.post()
          .uri(uriBuilder -> uriBuilder
              .path("/convert/images")
              .queryParam("format", actualFormat)
              .queryParam("dpi", actualDpi)
              .build())
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(BodyInserters.fromMultipartData(builder.build()))
          .retrieve()
          .bodyToFlux(String.class)
          .doOnNext(line -> {
            if (line != null && !line.isBlank()) {
              try {
                final ImagePage page = parseNdjsonLine(objectMapper, line);
                pageHandler.accept(page);
              } catch (final Exception e) {
                log.warn("NDJSON 라인 파싱 실패: {}", e.getMessage());
              }
            }
          })
          .blockLast();
    } catch (final Exception e) {
      throw new ParseKitClientException("Request failed", e);
    }
  }

  public boolean isAvailable() {
    try {
      // 빈 응답도 성공으로 처리하기 위해 Void로 변환
      webClient.get()
          .uri("/health")
          .retrieve()
          .toBodilessEntity()
          .block();
      return true;
    } catch (final Exception e) {
      log.debug("ParseKit 서비스 연결 불가: {}", e.getMessage());
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  private ConvertResult parseConvertData(final Object data) {
    if (data == null) {
      throw new ParseKitException(501, "ParseKit /convert: null data");
    }

    final Map<String, Object> dataMap = (Map<String, Object>) data;
    final String outputFilename = (String) dataMap.get("filename");
    final String base64Content = (String) dataMap.get("content");
    final int size = ((Number) dataMap.get("size")).intValue();
    final boolean converted = (Boolean) dataMap.get("converted");

    if (base64Content == null) {
      throw new ParseKitException(501, "ParseKit /convert: null content");
    }

    final byte[] pdfBytes = Base64.getDecoder().decode(base64Content);

    log.debug("문서 변환 완료: {} -> {} ({} bytes, converted={})", outputFilename, outputFilename, size, converted);
    return new ConvertResult(outputFilename, pdfBytes, size, converted);
  }

  /**
   * NDJSON 응답을 파싱하여 ImageConvertResult로 변환
   */
  private ImageConvertResult parseNdjsonResponse(final String responseBody, final String format) {
    final ObjectMapper objectMapper = new ObjectMapper();
    final List<ImagePage> pages = new ArrayList<>();
    int totalPages = 0;

    final String[] lines = responseBody.split("\n");
    for (final String line : lines) {
      if (line == null || line.isBlank()) {
        continue;
      }
      final ImagePage page = parseNdjsonLine(objectMapper, line);
      pages.add(page);
      totalPages = page.totalPages();
    }

    if (pages.isEmpty()) {
      throw new ParseKitException(501, "ParseKit /convert/images: no pages in response");
    }

    log.debug("이미지 변환 완료: {} 페이지, format={}", totalPages, format);
    return new ImageConvertResult(format, totalPages, pages);
  }

  /**
   * NDJSON 한 라인을 파싱하여 ImagePage로 변환
   */
  @SuppressWarnings("unchecked")
  private ImagePage parseNdjsonLine(final ObjectMapper objectMapper, final String line) {
    final Map<String, Object> pageData;
    try {
      pageData = objectMapper.readValue(line, Map.class);
    } catch (final Exception e) {
      throw new ParseKitException(501, "ParseKit /convert/images: failed to parse NDJSON line: " + e.getMessage());
    }

    final int page = ((Number) pageData.get("page")).intValue();
    final String base64Content = (String) pageData.get("content");
    final int size = ((Number) pageData.get("size")).intValue();
    final int totalPages = ((Number) pageData.get("total_pages")).intValue();

    if (base64Content == null) {
      throw new ParseKitException(501, "ParseKit /convert/images: null content for page " + page);
    }

    final byte[] imageBytes = Base64.getDecoder().decode(base64Content);
    return new ImagePage(page, imageBytes, size, totalPages);
  }
}

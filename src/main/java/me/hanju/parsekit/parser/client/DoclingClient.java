package me.hanju.parsekit.parser.client;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;
import me.hanju.parsekit.parser.config.ParserProperties;
import me.hanju.parsekit.parser.dto.DoclingConvertResponse;
import me.hanju.parsekit.parser.dto.ParseResult;
import me.hanju.parsekit.parser.exception.DoclingClientException;

@Slf4j
@Component
@RefreshScope
@ConditionalOnProperty(prefix = "parser.docling", name = "base-urls[0]")
public class DoclingClient {

  private final List<WebClient> webClients;
  private final AtomicInteger counter = new AtomicInteger(0);
  private final Duration timeout;

  public DoclingClient(ParserProperties properties) {
    ParserProperties.DoclingProperties docling = properties.getDocling();

    int bufferSize = docling.getMaxBufferSize() > 0 ? docling.getMaxBufferSize() : 16 * 1024 * 1024;
    ExchangeStrategies strategies = ExchangeStrategies.builder()
        .codecs(config -> config.defaultCodecs().maxInMemorySize(bufferSize))
        .build();

    this.webClients = docling.getBaseUrls().stream()
        .filter(url -> url != null && !url.isBlank())
        .map(url -> WebClient.builder()
            .baseUrl(url)
            .exchangeStrategies(strategies)
            .build())
        .toList();

    this.timeout = docling.getTimeout() != null ? docling.getTimeout() : Duration.ofMinutes(5);

    log.info("DoclingClient initialized with {} servers: {}", webClients.size(), docling.getBaseUrls());
  }

  private WebClient getNextClient() {
    int index = Math.abs(counter.getAndIncrement() % webClients.size());
    return webClients.get(index);
  }

  /**
   * Parse document to markdown
   */
  public ParseResult parse(byte[] fileBytes, String filename) {
    return parse(fileBytes, filename, "placeholder");
  }

  /**
   * Parse document to markdown with image handling mode
   *
   * @param imageMode "placeholder", "embedded", or "referenced"
   */
  public ParseResult parse(byte[] fileBytes, String filename, String imageMode) {
    if (fileBytes == null || fileBytes.length == 0) {
      throw new IllegalArgumentException("File bytes cannot be null or empty");
    }
    if (filename == null || filename.isBlank()) {
      throw new IllegalArgumentException("Filename cannot be null or empty");
    }

    log.debug("Parsing document: {} (size: {} bytes, imageMode: {})", filename, fileBytes.length, imageMode);

    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("files", new ByteArrayResource(fileBytes) {
      @Override
      public String getFilename() {
        return filename;
      }
    }).contentType(MediaType.APPLICATION_OCTET_STREAM);
    builder.part("image_export_mode", imageMode);

    try {
      DoclingConvertResponse response = getNextClient().post()
          .uri("/v1/convert/file")
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(BodyInserters.fromMultipartData(builder.build()))
          .retrieve()
          .bodyToMono(DoclingConvertResponse.class)
          .block(timeout);

      if (response == null || response.document() == null) {
        throw new DoclingClientException("Empty response from docling service",
            new IllegalStateException("Response is null"));
      }

      String mdContent = response.document().mdContent();
      if (mdContent == null) {
        throw new DoclingClientException("Invalid response format: missing 'md_content' field",
            new IllegalStateException("md_content is null"));
      }

      return new ParseResult(filename, mdContent);

    } catch (DoclingClientException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to parse document: {}", filename, e);
      throw new DoclingClientException("Failed to parse document: " + e.getMessage(), e);
    }
  }

  /**
   * Docling이 직접 지원하는 문서 형식.
   *
   * @see <a href="https://docling-project.github.io/docling/usage/supported_formats/">Docling Supported Formats</a>
   */
  private static final Set<String> SUPPORTED_TYPES = Set.of(
      // PDF
      "application/pdf",
      // MS Office 2007+ (OOXML)
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xlsx
      "application/vnd.openxmlformats-officedocument.presentationml.presentation", // .pptx
      // Markdown
      "text/markdown",
      "text/x-markdown",
      // HTML
      "text/html",
      "application/xhtml+xml",
      // CSV
      "text/csv",
      // Images
      "image/png",
      "image/jpeg",
      "image/tiff",
      "image/bmp",
      "image/webp"
  );

  /**
   * 주어진 MIME 타입이 Docling에서 직접 지원되는지 확인한다.
   */
  public static boolean isSupported(final String mimeType) {
    return SUPPORTED_TYPES.contains(mimeType);
  }

  /**
   * Markdown embedded 이미지 패턴: ![alt](data:image/xxx;base64,...)
   * 캡처 그룹: 1=altText, 2=mimeType(image/xxx), 3=base64Data
   */
  public static final Pattern EMBEDDED_IMAGE_PATTERN = Pattern.compile(
      "!\\[([^\\]]*)\\]\\(data:(image/[^;]+);base64,([^)]+)\\)");
}

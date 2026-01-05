package me.hanju.parsekit.parser.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.hanju.parsekit.common.FileTypeDetector;
import me.hanju.parsekit.common.FileTypeDetector.FileTypeInfo;
import me.hanju.parsekit.common.exception.BadRequestException;
import me.hanju.parsekit.common.exception.UnsupportedMediaTypeException;
import me.hanju.parsekit.converter.service.JodConverterService;
import me.hanju.parsekit.parser.client.DoclingClient;
import me.hanju.parsekit.parser.client.VlmClient;
import me.hanju.parsekit.parser.dto.ParseResult;

/**
 * Docling 전용 파싱 컨트롤러
 * - 플레인 텍스트: 지원 안함
 * - 마크다운: embedded 이미지를 placeholder로 대체 후 반환
 * - Docling 지원 형식 (PDF, DOCX, XLSX, PPTX, HTML, CSV, 이미지): 바로 파싱
 * - 기타 문서: PDF 변환 후 파싱
 */
@Slf4j
@RestController
@RequestMapping("/api/parse")
@RequiredArgsConstructor
@ConditionalOnBean(DoclingClient.class)
@ConditionalOnMissingBean(VlmClient.class)
public class DoclingParserController {

  private final DoclingClient doclingClient;
  private final JodConverterService jodConverter;

  private static final String IMAGE_MODE = "placeholder";

  @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ParseResult> parse(@RequestParam("file") final MultipartFile file) {
    if (file.isEmpty()) {
      throw new BadRequestException("File is empty");
    }

    final FileTypeInfo info = FileTypeDetector.detect(file);

    final ParseResult result = switch (info.category()) {
      case PLAIN_TEXT ->
        throw new UnsupportedMediaTypeException("Plain text files not supported: " + info.originalFilename());
      case MARKDOWN -> {
        log.info("Markdown file, replacing embedded images with placeholders: {}", info.originalFilename());
        final String content = new String(FileTypeDetector.getBytes(file), StandardCharsets.UTF_8);
        final String processed = replaceEmbeddedImagesWithPlaceholder(content);
        yield new ParseResult(info.originalFilename(), processed);
      }
      case IMAGE -> {
        if (!DoclingClient.isSupported(info.mimeType())) {
          throw new UnsupportedMediaTypeException("Image type not supported by Docling: " + info.mimeType());
        }
        log.info("Parsing image with Docling: {}", info.originalFilename());
        final byte[] fileBytes = FileTypeDetector.getBytes(file);
        yield doclingClient.parse(fileBytes, info.originalFilename(), IMAGE_MODE);
      }
      case DOCUMENT, SPREADSHEET, PRESENTATION, PDF -> {
        final byte[] fileBytes = FileTypeDetector.getBytes(file);
        if (DoclingClient.isSupported(info.mimeType())) {
          log.info("Parsing with Docling: {}", info.originalFilename());
          yield doclingClient.parse(fileBytes, info.originalFilename(), IMAGE_MODE);
        } else {
          log.info("Converting to PDF, then parsing: {}", info.originalFilename());
          final byte[] pdfBytes = jodConverter.convertToPdf(fileBytes);
          yield doclingClient.parse(pdfBytes, info.baseFilename() + ".pdf", IMAGE_MODE);
        }
      }
    };

    return ResponseEntity.ok(result);
  }

  /**
   * Markdown 내 embedded 이미지를 placeholder로 대체한다.
   */
  private String replaceEmbeddedImagesWithPlaceholder(String markdown) {
    return DoclingClient.EMBEDDED_IMAGE_PATTERN.matcher(markdown)
        .replaceAll(match -> {
          String altText = match.group(1);
          return altText.isBlank() ? "<!-- image -->" : "<!-- image: " + altText + " -->";
        });
  }
}

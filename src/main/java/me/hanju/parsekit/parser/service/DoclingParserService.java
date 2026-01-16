package me.hanju.parsekit.parser.service;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.hanju.parsekit.common.FileTypeDetector;
import me.hanju.parsekit.common.FileTypeDetector.FileTypeInfo;
import me.hanju.parsekit.common.exception.UnsupportedMediaTypeException;
import me.hanju.parsekit.converter.service.JodConverterService;
import me.hanju.parsekit.parser.client.DoclingClient;
import me.hanju.parsekit.parser.client.VlmClient;
import me.hanju.parsekit.parser.dto.ParseResult;

/**
 * Docling 전용 파서 서비스.
 * - 플레인 텍스트: 지원 안함
 * - 마크다운: embedded 이미지를 placeholder로 대체 후 반환
 * - Docling 지원 형식 (PDF, DOCX, XLSX, PPTX, HTML, CSV, 이미지): 바로 파싱
 * - 기타 문서: PDF 변환 후 파싱
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(DoclingClient.class)
@ConditionalOnMissingBean(VlmClient.class)
public class DoclingParserService implements IParserService {

  private final DoclingClient doclingClient;
  private final JodConverterService jodConverter;

  private static final String IMAGE_MODE = "placeholder";

  @Override
  public ParseResult parse(byte[] content, String filename, int dpi) {
    final FileTypeInfo info = FileTypeDetector.detect(content, filename);

    return switch (info.category()) {
      case PLAIN_TEXT ->
        throw new UnsupportedMediaTypeException("Plain text files not supported: " + filename);
      case MARKDOWN -> {
        log.info("Markdown file, replacing embedded images with placeholders: {}", filename);
        final String markdownContent = new String(content, StandardCharsets.UTF_8);
        final String processed = replaceEmbeddedImagesWithPlaceholder(markdownContent);
        yield new ParseResult(filename, processed);
      }
      case IMAGE -> {
        if (!DoclingClient.isSupported(info.mimeType())) {
          throw new UnsupportedMediaTypeException("Image type not supported by Docling: " + info.mimeType());
        }
        log.info("Parsing image with Docling: {}", filename);
        yield doclingClient.parse(content, filename, IMAGE_MODE);
      }
      case DOCUMENT, SPREADSHEET, PRESENTATION, PDF -> {
        if (DoclingClient.isSupported(info.mimeType())) {
          log.info("Parsing with Docling: {}", filename);
          yield doclingClient.parse(content, filename, IMAGE_MODE);
        } else {
          log.info("Converting to PDF, then parsing: {}", filename);
          final byte[] pdfBytes = jodConverter.convertToPdf(content);
          final ParseResult doclingResult = doclingClient.parse(pdfBytes, info.baseFilename() + ".pdf", IMAGE_MODE);
          yield new ParseResult(filename, doclingResult.markdown());
        }
      }
    };
  }

  private String replaceEmbeddedImagesWithPlaceholder(String markdown) {
    return DoclingClient.EMBEDDED_IMAGE_PATTERN.matcher(markdown)
        .replaceAll(match -> {
          String altText = match.group(1);
          return altText.isBlank() ? "<!-- image -->" : "<!-- image: " + altText + " -->";
        });
  }
}

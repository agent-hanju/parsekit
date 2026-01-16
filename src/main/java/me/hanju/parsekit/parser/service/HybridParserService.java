package me.hanju.parsekit.parser.service;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.hanju.parsekit.common.FileTypeDetector;
import me.hanju.parsekit.common.FileTypeDetector.FileTypeInfo;
import me.hanju.parsekit.common.exception.UnsupportedMediaTypeException;
import me.hanju.parsekit.converter.service.JodConverterService;
import me.hanju.parsekit.parser.client.DoclingClient;
import me.hanju.parsekit.parser.client.VlmClient;
import me.hanju.parsekit.parser.config.ParserProperties;
import me.hanju.parsekit.parser.dto.ParseResult;

/**
 * 하이브리드 파서 서비스 (Docling + VLM).
 * - 플레인 텍스트: 지원 안함
 * - 마크다운: embedded 이미지를 VLM OCR로 대체 후 반환
 * - 이미지/문서/스프레드시트/프레젠테이션/PDF: Docling embedded 모드로 파싱 후 이미지를 VLM OCR로 대체
 * - 기타 문서: PDF 변환 → Docling 파싱 → 이미지 VLM OCR
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean({ DoclingClient.class, VlmClient.class })
public class HybridParserService implements IParserService {

  private final DoclingClient doclingClient;
  private final VlmClient vlmClient;
  private final JodConverterService jodConverter;
  private final ParserProperties parserProperties;

  private static final String IMAGE_MODE = "embedded";

  @Override
  public ParseResult parse(byte[] content, String filename, int dpi) {
    final FileTypeInfo info = FileTypeDetector.detect(content, filename);

    return switch (info.category()) {
      case PLAIN_TEXT ->
        throw new UnsupportedMediaTypeException("Plain text files not supported: " + filename);
      case MARKDOWN -> {
        log.info("Markdown file, replacing embedded images with VLM OCR: {}", filename);
        final String markdownContent = new String(content, StandardCharsets.UTF_8);
        final String markdown = replaceEmbeddedImages(markdownContent);
        yield new ParseResult(filename, markdown);
      }
      case IMAGE -> {
        log.info("Image file, OCR with VLM directly: {}", filename);
        final String encodedUri = FileTypeDetector.toBase64EncodedUri(info.mimeType(), content);
        final String ocrResult = vlmClient.ocr(encodedUri);
        yield new ParseResult(filename, ocrResult);
      }
      case DOCUMENT, SPREADSHEET, PRESENTATION, PDF -> {
        final ParseResult doclingResult;
        if (DoclingClient.isSupported(info.mimeType())) {
          log.info("Parsing with Docling: {}", filename);
          doclingResult = doclingClient.parse(content, filename, IMAGE_MODE);
        } else {
          log.info("Converting to PDF, then parsing: {}", filename);
          final byte[] pdfBytes = jodConverter.convertToPdf(content);
          doclingResult = doclingClient.parse(pdfBytes, info.baseFilename() + ".pdf", IMAGE_MODE);
        }
        final String markdown = replaceEmbeddedImages(doclingResult.markdown());
        yield new ParseResult(filename, markdown);
      }
    };
  }

  private String replaceEmbeddedImages(String markdown) {
    Matcher matcher = DoclingClient.EMBEDDED_IMAGE_PATTERN.matcher(markdown);
    StringBuffer result = new StringBuffer();

    int count = 0;
    while (matcher.find()) {
      count++;
      String altText = matcher.group(1);
      String imageMimeType = matcher.group(2);
      String base64Data = matcher.group(3);

      try {
        byte[] imageBytes = FileTypeDetector.decodeBase64(base64Data);
        String prompt = buildPrompt(altText);
        String ocrResult = vlmClient.ocr(FileTypeDetector.toBase64EncodedUri(imageMimeType, imageBytes), prompt);
        matcher.appendReplacement(result, Matcher.quoteReplacement(ocrResult));
      } catch (Exception e) {
        log.warn("Failed to OCR image {}: {}", count, e.getMessage());
        matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
      }
    }
    matcher.appendTail(result);

    if (count > 0) {
      log.info("Replaced {} embedded images with VLM results", count);
    }
    return result.toString();
  }

  private String buildPrompt(String altText) {
    if (altText != null && !altText.isBlank()) {
      return String.format(
          "This is an embedded image with alt text: \"%s\". " +
              "Extract and describe all text, diagrams, charts, or visual content. " +
              "Format the output as markdown.",
          altText);
    }
    return parserProperties.getVlm().getEmbeddedImagePrompt();
  }
}

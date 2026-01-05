package me.hanju.parsekit.parser.controller;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
import me.hanju.parsekit.parser.config.ParserProperties;
import me.hanju.parsekit.parser.dto.ParseResult;

/**
 * 하이브리드 파싱 컨트롤러 (Docling + VLM)
 * - 플레인 텍스트: 지원 안함
 * - 마크다운: embedded 이미지를 VLM OCR로 대체 후 반환
 * - 이미지/문서/스프레드시트/프레젠테이션/PDF: Docling embedded 모드로 파싱 후 이미지를 VLM OCR로 대체
 * - 기타 문서: PDF 변환 → Docling 파싱 → 이미지 VLM OCR
 */
@Slf4j
@RestController
@RequestMapping("/api/parse")
@RequiredArgsConstructor
@ConditionalOnBean({ DoclingClient.class, VlmClient.class })
public class HybridParserController {

  private final DoclingClient doclingClient;
  private final VlmClient vlmClient;
  private final JodConverterService jodConverter;
  private final ParserProperties parserProperties;

  private static final String IMAGE_MODE = "embedded";

  @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ParseResult> parse(
      @RequestParam("file") final MultipartFile file,
      @RequestParam(value = "dpi", defaultValue = "150") final int dpi) {
    if (file.isEmpty()) {
      throw new BadRequestException("File is empty");
    }

    final FileTypeInfo info = FileTypeDetector.detect(file);
    final ParseResult result = switch (info.category()) {
      case PLAIN_TEXT ->
        throw new UnsupportedMediaTypeException("Plain text files not supported: " + info.originalFilename());
      case MARKDOWN -> {
        log.info("Markdown file, replacing embedded images with VLM OCR: {}", info.originalFilename());
        final String content = new String(FileTypeDetector.getBytes(file), StandardCharsets.UTF_8);
        final String markdown = this.replaceEmbeddedImages(content);
        yield new ParseResult(info.originalFilename(), markdown);
      }
      case IMAGE -> {
        log.info("Image file, OCR with VLM directly: {}", info.originalFilename());
        final String encodedUri = FileTypeDetector.toBase64EncodedUri(file);
        final String ocrResult = vlmClient.ocr(encodedUri);
        yield new ParseResult(info.originalFilename(), ocrResult);
      }
      case DOCUMENT, SPREADSHEET, PRESENTATION, PDF -> {
        final byte[] fileBytes = FileTypeDetector.getBytes(file);
        final ParseResult doclingResult;
        if (DoclingClient.isSupported(info.mimeType())) {
          log.info("Parsing with Docling: {}", info.originalFilename());
          doclingResult = doclingClient.parse(fileBytes, info.originalFilename(), IMAGE_MODE);
        } else {
          log.info("Converting to PDF, then parsing: {}", info.originalFilename());
          final byte[] pdfBytes = jodConverter.convertToPdf(fileBytes);
          doclingResult = doclingClient.parse(pdfBytes, info.baseFilename() + ".pdf", IMAGE_MODE);
        }
        final String markdown = this.replaceEmbeddedImages(doclingResult.markdown());
        yield new ParseResult(info.originalFilename(), markdown);
      }
    };

    return ResponseEntity.ok(result);
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

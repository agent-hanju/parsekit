package me.hanju.parsekit.parser.controller;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
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
import me.hanju.parsekit.converter.service.JodConverterService;
import me.hanju.parsekit.parser.client.DoclingClient;
import me.hanju.parsekit.parser.client.VlmClient;
import me.hanju.parsekit.parser.config.ParserProperties;
import me.hanju.parsekit.parser.dto.ParseResult;

/**
 * 하이브리드 파싱 컨트롤러 (Docling + VLM)
 * - Docling으로 문서 파싱 (embedded 모드)
 * - 결과 마크다운에서 이미지를 VLM으로 OCR하여 대체
 */
@Slf4j
@RestController
@RequestMapping("/api/parse")
@RequiredArgsConstructor
@ConditionalOnBean({ DoclingClient.class, VlmClient.class })
public class HybridParserController {

  private static final Pattern EMBEDDED_IMAGE_PATTERN = Pattern.compile(
      "!\\[([^\\]]*)\\]\\(data:image/[^;]+;base64,([^)]+)\\)");

  private final DoclingClient doclingClient;
  private final VlmClient vlmClient;
  private final JodConverterService jodConverter;
  private final FileTypeDetector fileTypeDetector;
  private final ParserProperties parserProperties;

  @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ParseResult> parse(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "dpi", defaultValue = "150") int dpi) {

    if (file.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }

    try {
      byte[] fileBytes = file.getBytes();
      String filename = file.getOriginalFilename();
      String mimeType = fileTypeDetector.detectMimeType(fileBytes, filename);

      // 이미지: VLM으로 직접 처리
      if (fileTypeDetector.isImage(mimeType)) {
        log.info("Image file, using VLM: {}", filename);
        String text = vlmClient.ocr(fileBytes, parserProperties.getVlm().getEmbeddedImagePrompt());
        return ResponseEntity.ok(new ParseResult(filename, text));
      }

      // 텍스트 파일: 변환 없이 그대로 반환
      if (fileTypeDetector.isText(mimeType)) {
        log.info("Text file, returning as-is: {}", filename);
        String content = new String(fileBytes, StandardCharsets.UTF_8);
        return ResponseEntity.ok(new ParseResult(filename, content));
      }

      // Docling 미지원 형식: PDF 변환
      byte[] parseBytes = fileBytes;
      String parseFilename = filename;
      if (!fileTypeDetector.isDoclingSupported(mimeType)) {
        log.info("Converting to PDF: {}", filename);
        parseBytes = jodConverter.convertToPdf(filename, fileBytes);
        parseFilename = filename.replaceAll("\\.[^.]+$", ".pdf");
      }

      // Docling으로 파싱 (embedded 모드)
      log.info("Parsing with Docling (embedded): {}", parseFilename);
      ParseResult doclingResult = doclingClient.parse(parseBytes, parseFilename, "embedded");

      // 임베디드 이미지를 VLM으로 대체
      String markdown = replaceEmbeddedImages(doclingResult.markdown());
      return ResponseEntity.ok(new ParseResult(filename, markdown));

    } catch (Exception e) {
      log.error("Parsing failed", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  private String replaceEmbeddedImages(String markdown) {
    Matcher matcher = EMBEDDED_IMAGE_PATTERN.matcher(markdown);
    StringBuffer result = new StringBuffer();

    int count = 0;
    while (matcher.find()) {
      count++;
      String altText = matcher.group(1);
      String base64Data = matcher.group(2);

      try {
        byte[] imageBytes = Base64.getDecoder().decode(base64Data);
        String prompt = buildPrompt(altText);
        String ocrResult = vlmClient.ocr(imageBytes, prompt);
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

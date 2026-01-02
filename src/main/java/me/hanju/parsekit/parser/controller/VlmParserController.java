package me.hanju.parsekit.parser.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
import me.hanju.parsekit.converter.service.PopplerConverterService;
import me.hanju.parsekit.converter.service.PopplerConverterService.PageImage;
import me.hanju.parsekit.parser.client.DoclingClient;
import me.hanju.parsekit.parser.client.VlmClient;
import me.hanju.parsekit.parser.config.ParserProperties;
import me.hanju.parsekit.parser.dto.ParseResult;

/**
 * VLM 전용 파싱 컨트롤러
 * - 이미지: 바로 OCR
 * - PDF: 이미지 변환 후 OCR
 * - 텍스트 파일: 변환 없이 그대로 반환
 * - 기타 문서: PDF 변환 → 이미지 변환 → OCR
 */
@Slf4j
@RestController
@RequestMapping("/api/parse")
@RequiredArgsConstructor
@ConditionalOnBean(VlmClient.class)
@ConditionalOnMissingBean(DoclingClient.class)
public class VlmParserController {

  private final VlmClient vlmClient;
  private final JodConverterService jodConverter;
  private final PopplerConverterService popplerConverter;
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

      // 텍스트 파일: 변환 없이 그대로 반환
      if (fileTypeDetector.isText(mimeType)) {
        log.info("Text file, returning as-is: {}", filename);
        String content = new String(fileBytes, StandardCharsets.UTF_8);
        return ResponseEntity.ok(new ParseResult(filename, content));
      }

      // 이미지: 바로 OCR
      if (fileTypeDetector.isImage(mimeType)) {
        log.info("Image file, OCR directly: {}", filename);
        String text = vlmClient.ocr(fileBytes, parserProperties.getVlm().getDefaultPrompt());
        return ResponseEntity.ok(new ParseResult(filename, text));
      }

      // PDF: 이미지 변환 후 OCR
      byte[] pdfBytes = fileBytes;
      if (!fileTypeDetector.isPdf(mimeType)) {
        log.info("Converting to PDF: {}", filename);
        pdfBytes = jodConverter.convertToPdf(filename, fileBytes);
      }

      log.info("Converting PDF to images (dpi={}): {}", dpi, filename);
      List<PageImage> images = popplerConverter.convertPdfToImages(
          pdfBytes, parserProperties.getVlm().getImageFormat(), dpi);

      StringBuilder markdown = new StringBuilder();
      for (PageImage image : images) {
        if (!markdown.isEmpty()) {
          markdown.append("\n\n---\n\n");
        }
        markdown.append(vlmClient.ocr(image.content(), parserProperties.getVlm().getDefaultPrompt()));
      }

      return ResponseEntity.ok(new ParseResult(filename, markdown.toString()));

    } catch (Exception e) {
      log.error("Parsing failed", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }
}

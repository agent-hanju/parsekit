package me.hanju.parsekit.parser.controller;

import java.nio.charset.StandardCharsets;

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
import me.hanju.parsekit.parser.client.DoclingClient;
import me.hanju.parsekit.parser.client.VlmClient;
import me.hanju.parsekit.parser.dto.ParseResult;

/**
 * Docling 전용 파싱 컨트롤러
 * - Docling 지원 형식: 바로 파싱
 * - 텍스트 파일: 변환 없이 그대로 반환
 * - 기타 문서: PDF 변환 후 파싱
 * - 이미지: 지원 안함
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
  private final FileTypeDetector fileTypeDetector;

  private static final String IMAGE_MODE = "placeholder";

  @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ParseResult> parse(@RequestParam("file") MultipartFile file) {

    if (file.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }

    try {
      byte[] fileBytes = file.getBytes();
      String filename = file.getOriginalFilename();
      String mimeType = fileTypeDetector.detectMimeType(fileBytes, filename);

      // 이미지는 Docling에서 지원 안함
      if (fileTypeDetector.isImage(mimeType)) {
        log.warn("Image files not supported: {}", filename);
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
      }

      // 텍스트 파일: 변환 없이 그대로 반환
      if (fileTypeDetector.isText(mimeType)) {
        log.info("Text file, returning as-is: {}", filename);
        String content = new String(fileBytes, StandardCharsets.UTF_8);
        return ResponseEntity.ok(new ParseResult(filename, content));
      }

      // Docling 지원 형식: 바로 파싱
      if (fileTypeDetector.isDoclingSupported(mimeType)) {
        log.info("Parsing with Docling: {}", filename);
        return ResponseEntity.ok(doclingClient.parse(fileBytes, filename, IMAGE_MODE));
      }

      // 기타 문서: PDF 변환 후 파싱
      log.info("Converting to PDF, then parsing: {}", filename);
      byte[] pdfBytes = jodConverter.convertToPdf(filename, fileBytes);
      String pdfFilename = filename.replaceAll("\\.[^.]+$", ".pdf");
      return ResponseEntity.ok(doclingClient.parse(pdfBytes, pdfFilename, IMAGE_MODE));

    } catch (Exception e) {
      log.error("Parsing failed", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }
}

package me.hanju.parsekit.parser.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
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
import me.hanju.parsekit.common.exception.ParseKitException;
import me.hanju.parsekit.common.exception.UnsupportedMediaTypeException;
import me.hanju.parsekit.parser.client.DoclingClient;
import me.hanju.parsekit.parser.client.VlmClient;
import me.hanju.parsekit.parser.dto.ParseResult;
import me.hanju.parsekit.parser.exception.TikaParserException;

/**
 * Tika 전용 파싱 컨트롤러 (Fallback).
 * Docling과 VLM이 설정되지 않았을 때 순정 Tika Parser로 텍스트를 추출한다.
 * - 플레인 텍스트: 변환 없이 그대로 반환
 * - 이미지: 지원 안함 (OCR 불가)
 * - 기타 문서: Tika Parser로 텍스트 추출
 */
@Slf4j
@RestController
@RequestMapping("/api/parse")
@RequiredArgsConstructor
@ConditionalOnMissingBean({ DoclingClient.class, VlmClient.class })
public class TikaParserController {

  private static final Parser PARSER = new AutoDetectParser();

  // Tika의 기본 출력 제한 (-1 = 무제한)
  private static final int WRITE_LIMIT = -1;

  @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ParseResult> parse(@RequestParam("file") final MultipartFile file) {
    if (file.isEmpty()) {
      throw new BadRequestException("File is empty");
    }

    final FileTypeInfo info = FileTypeDetector.detect(file);

    final ParseResult result = switch (info.category()) {
      case PLAIN_TEXT, MARKDOWN -> {
        log.info("Plain text file, returning as-is: {}", info.originalFilename());
        final String content = new String(this.getFileBytes(file), StandardCharsets.UTF_8);
        yield new ParseResult(info.originalFilename(), content);
      }
      case IMAGE ->
        throw new UnsupportedMediaTypeException("Image files not supported without VLM: " + info.originalFilename());
      case DOCUMENT, SPREADSHEET, PRESENTATION, PDF -> {
        log.info("Parsing with Tika: {}", info.originalFilename());
        final String extractedText = this.extractText(this.getFileBytes(file));
        yield new ParseResult(info.originalFilename(), extractedText);
      }
    };

    return ResponseEntity.ok(result);
  }

  /**
   * Tika Parser를 사용하여 문서에서 텍스트를 추출한다.
   */
  private String extractText(byte[] content) {
    BodyContentHandler handler = new BodyContentHandler(WRITE_LIMIT);
    Metadata metadata = new Metadata();
    ParseContext context = new ParseContext();

    try (ByteArrayInputStream stream = new ByteArrayInputStream(content)) {
      PARSER.parse(stream, handler, metadata, context);
    } catch (Exception e) {
      throw new TikaParserException("Failed to extract text from document", e);
    }

    return handler.toString().trim();
  }

  private byte[] getFileBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new ParseKitException("Failed to read uploaded file", e);
    }
  }
}

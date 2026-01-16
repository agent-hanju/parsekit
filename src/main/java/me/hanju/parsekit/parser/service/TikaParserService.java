package me.hanju.parsekit.parser.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import me.hanju.parsekit.common.FileTypeDetector;
import me.hanju.parsekit.common.FileTypeDetector.FileTypeInfo;
import me.hanju.parsekit.common.exception.UnsupportedMediaTypeException;
import me.hanju.parsekit.parser.client.DoclingClient;
import me.hanju.parsekit.parser.client.VlmClient;
import me.hanju.parsekit.parser.dto.ParseResult;
import me.hanju.parsekit.parser.exception.TikaParserException;

/**
 * Tika 전용 파서 서비스 (Fallback).
 * Docling과 VLM이 설정되지 않았을 때 순정 Tika Parser로 텍스트를 추출한다.
 * - 플레인 텍스트: 변환 없이 그대로 반환
 * - 이미지: 지원 안함 (OCR 불가)
 * - 기타 문서: Tika Parser로 텍스트 추출
 */
@Slf4j
@Service
@ConditionalOnMissingBean({ DoclingClient.class, VlmClient.class })
public class TikaParserService implements IParserService {

  private static final Parser PARSER = new AutoDetectParser();
  private static final int WRITE_LIMIT = -1;

  @Override
  public ParseResult parse(byte[] content, String filename, int dpi) {
    final FileTypeInfo info = FileTypeDetector.detect(content, filename);

    return switch (info.category()) {
      case PLAIN_TEXT, MARKDOWN -> {
        log.info("Plain text file, returning as-is: {}", filename);
        final String textContent = new String(content, StandardCharsets.UTF_8);
        yield new ParseResult(filename, textContent);
      }
      case IMAGE ->
        throw new UnsupportedMediaTypeException("Image files not supported without VLM: " + filename);
      case DOCUMENT, SPREADSHEET, PRESENTATION, PDF -> {
        log.info("Parsing with Tika: {}", filename);
        final String extractedText = extractText(content);
        yield new ParseResult(filename, extractedText);
      }
    };
  }

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
}

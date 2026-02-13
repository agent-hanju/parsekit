package dev.hanju.parsekit.parser.service;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import dev.hanju.parsekit.common.FileTypeDetector;
import dev.hanju.parsekit.common.FileTypeDetector.FileTypeInfo;
import dev.hanju.parsekit.common.exception.UnsupportedMediaTypeException;
import dev.hanju.parsekit.converter.service.JodConverterService;
import dev.hanju.parsekit.converter.service.MarkdownService;
import dev.hanju.parsekit.converter.service.PopplerConverterService;
import dev.hanju.parsekit.converter.service.PopplerConverterService.PageImage;
import dev.hanju.parsekit.parser.client.DoclingClient;
import dev.hanju.parsekit.parser.client.VlmClient;
import dev.hanju.parsekit.parser.config.ParserProperties;
import dev.hanju.parsekit.parser.dto.ParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * VLM 전용 파서 서비스.
 * - 플레인 텍스트: 지원 안함
 * - 마크다운: HTML → PDF → 이미지 변환 후 OCR
 * - 문서/스프레드시트/프레젠테이션: PDF → 이미지 변환 후 OCR
 * - PDF: 이미지 변환 후 OCR
 * - 이미지: 바로 OCR
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(VlmClient.class)
@ConditionalOnMissingBean(DoclingClient.class)
public class VlmParserService implements IParserService {

  private final VlmClient vlmClient;
  private final MarkdownService markdownService;
  private final JodConverterService jodConverter;
  private final PopplerConverterService popplerConverter;
  private final ParserProperties parserProperties;

  @Override
  public ParseResult parse(byte[] content, String filename, int dpi) {
    final FileTypeInfo info = FileTypeDetector.detect(content, filename);

    final String imageFormat = parserProperties.getVlm().getImageFormat();
    final String imageMimeType = "image/" + imageFormat;

    final List<PageImage> images = switch (info.category()) {
      case PLAIN_TEXT ->
        throw new UnsupportedMediaTypeException("Plain text files not supported: " + filename);
      case MARKDOWN -> {
        log.info("Converting Markdown to HTML to PDF to images: {}", filename);
        final byte[] htmlBytes = markdownService.convertToFullHtml(content, info.baseFilename());
        final byte[] pdfBytes = jodConverter.convertToPdf(htmlBytes);
        yield popplerConverter.convertPdfToImages(pdfBytes, imageFormat, dpi);
      }
      case DOCUMENT, SPREADSHEET, PRESENTATION -> {
        log.info("Converting to PDF to images: {}", filename);
        final byte[] pdfBytes = jodConverter.convertToPdf(content);
        yield popplerConverter.convertPdfToImages(pdfBytes, imageFormat, dpi);
      }
      case PDF -> {
        log.info("Converting PDF to images: {}", filename);
        yield popplerConverter.convertPdfToImages(content, imageFormat, dpi);
      }
      case IMAGE -> {
        log.info("Image file, passing through: {}", filename);
        yield List.of(new PageImage(1, imageFormat, content, 1));
      }
    };

    final StringBuilder markdown = new StringBuilder();
    for (final PageImage image : images) {
      if (!markdown.isEmpty()) {
        markdown.append("\n\n---\n\n");
      }
      markdown.append(vlmClient.ocr(
          FileTypeDetector.toBase64EncodedUri(imageMimeType, image.content()),
          parserProperties.getVlm().getDefaultPrompt()));
    }

    return new ParseResult(filename, markdown.toString());
  }
}

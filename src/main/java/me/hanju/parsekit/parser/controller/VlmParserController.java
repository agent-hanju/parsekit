package me.hanju.parsekit.parser.controller;

import java.util.List;

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
import me.hanju.parsekit.converter.service.MarkdownService;
import me.hanju.parsekit.converter.service.PopplerConverterService;
import me.hanju.parsekit.converter.service.PopplerConverterService.PageImage;
import me.hanju.parsekit.parser.client.DoclingClient;
import me.hanju.parsekit.parser.client.VlmClient;
import me.hanju.parsekit.parser.config.ParserProperties;
import me.hanju.parsekit.parser.dto.ParseResult;

/**
 * VLM 전용 파싱 컨트롤러
 * - 플레인 텍스트: 지원 안함
 * - 마크다운: HTML → PDF → 이미지 변환 후 OCR
 * - 문서/스프레드시트/프레젠테이션: PDF → 이미지 변환 후 OCR
 * - PDF: 이미지 변환 후 OCR
 * - 이미지: 바로 OCR
 */
@Slf4j
@RestController
@RequestMapping("/api/parse")
@RequiredArgsConstructor
@ConditionalOnBean(VlmClient.class)
@ConditionalOnMissingBean(DoclingClient.class)
public class VlmParserController {

  private final VlmClient vlmClient;
  private final MarkdownService markdownService;
  private final JodConverterService jodConverter;
  private final PopplerConverterService popplerConverter;
  private final ParserProperties parserProperties;

  @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ParseResult> parse(
      @RequestParam("file") final MultipartFile file,
      @RequestParam(value = "dpi", defaultValue = "150") final int dpi) {
    if (file.isEmpty()) {
      throw new BadRequestException("File is empty");
    }

    final FileTypeInfo info = FileTypeDetector.detect(file);

    final String imageFormat = parserProperties.getVlm().getImageFormat();
    final String imageMimeType = "image/" + imageFormat;

    final List<PageImage> images = switch (info.category()) {
      case PLAIN_TEXT ->
        throw new UnsupportedMediaTypeException("Plain text files not supported: " + info.originalFilename());
      case MARKDOWN -> {
        log.info("Converting Markdown to HTML to PDF to images: {}", info.originalFilename());
        final byte[] htmlBytes = markdownService.convertToFullHtml(FileTypeDetector.getBytes(file), info.baseFilename());
        final byte[] pdfBytes = jodConverter.convertToPdf(htmlBytes);
        yield popplerConverter.convertPdfToImages(pdfBytes, imageFormat, dpi);
      }
      case DOCUMENT, SPREADSHEET, PRESENTATION -> {
        log.info("Converting to PDF to images: {}", info.originalFilename());
        final byte[] pdfBytes = jodConverter.convertToPdf(FileTypeDetector.getBytes(file));
        yield popplerConverter.convertPdfToImages(pdfBytes, imageFormat, dpi);
      }
      case PDF -> {
        log.info("Converting PDF to images: {}", info.originalFilename());
        yield popplerConverter.convertPdfToImages(FileTypeDetector.getBytes(file), imageFormat, dpi);
      }
      case IMAGE -> {
        log.info("Image file, passing through: {}", info.originalFilename());
        yield List.of(new PageImage(1, imageFormat, FileTypeDetector.getBytes(file), 1));
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
    final ParseResult result = new ParseResult(info.originalFilename(), markdown.toString());

    return ResponseEntity.ok(result);
  }
}

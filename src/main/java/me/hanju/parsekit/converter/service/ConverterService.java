package me.hanju.parsekit.converter.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.hanju.parsekit.common.FileTypeDetector;
import me.hanju.parsekit.common.FileTypeDetector.FileTypeInfo;
import me.hanju.parsekit.common.exception.BadRequestException;
import me.hanju.parsekit.common.exception.UnsupportedMediaTypeException;
import me.hanju.parsekit.converter.service.PopplerConverterService.PageImage;

/**
 * 파일 형식에 따른 변환 분기를 처리하는 래퍼 서비스.
 * 내부에서도 동일한 변환 기능을 호출할 수 있도록 byte[] 기반 API를 제공한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConverterService {

  private final JodConverterService jodService;
  private final PopplerConverterService popplerService;
  private final MarkdownService markdownService;

  /**
   * ODT 형식으로 변환한다.
   *
   * @param content  파일 내용
   * @param filename 원본 파일명
   * @return ODT 파일 바이트
   */
  public byte[] convertToOdt(byte[] content, String filename) {
    final FileTypeInfo info = FileTypeDetector.detect(content, filename);

    final byte[] inputBytes = switch (info.category()) {
      case DOCUMENT, PLAIN_TEXT -> {
        if (".odt".equalsIgnoreCase(info.extension())) {
          throw new BadRequestException("File is already in ODT format");
        }
        yield content;
      }
      case MARKDOWN ->
        markdownService.convertToFullHtml(content, info.baseFilename());
      default ->
        throw new UnsupportedMediaTypeException(
            "Unsupported file type for ODT conversion: " + filename);
    };

    log.info("Converting to ODT: {}", filename);
    return jodService.convertToOdt(inputBytes);
  }

  /**
   * PDF 형식으로 변환한다.
   *
   * @param content  파일 내용
   * @param filename 원본 파일명
   * @return PDF 파일 바이트
   */
  public byte[] convertToPdf(byte[] content, String filename) {
    final FileTypeInfo info = FileTypeDetector.detect(content, filename);

    final byte[] inputBytes = switch (info.category()) {
      case DOCUMENT, SPREADSHEET, PRESENTATION, PLAIN_TEXT ->
        content;
      case MARKDOWN ->
        markdownService.convertToFullHtml(content, info.baseFilename());
      case PDF ->
        throw new BadRequestException("File is already in PDF format");
      default ->
        throw new UnsupportedMediaTypeException(
            "Unsupported file type for PDF conversion: " + filename);
    };

    log.info("Converting to PDF: {}", filename);
    return jodService.convertToPdf(inputBytes);
  }

  /**
   * 이미지로 변환한다 (PDF를 거쳐 이미지로 변환).
   *
   * @param content  파일 내용
   * @param filename 원본 파일명
   * @param format   이미지 형식 (png, jpeg 등)
   * @param dpi      해상도
   * @return 페이지별 이미지 목록
   */
  public List<PageImage> convertToImages(byte[] content, String filename, String format, int dpi) {
    final FileTypeInfo info = FileTypeDetector.detect(content, filename);

    final byte[] pdfBytes = switch (info.category()) {
      case DOCUMENT, SPREADSHEET, PRESENTATION, PLAIN_TEXT ->
        jodService.convertToPdf(content);
      case MARKDOWN ->
        jodService.convertToPdf(markdownService.convertToFullHtml(content, info.baseFilename()));
      case PDF ->
        content;
      default ->
        throw new UnsupportedMediaTypeException(
            "Unsupported file type for image conversion: " + filename);
    };

    log.info("Converting to images: {} (format={}, dpi={})", filename, format, dpi);
    return popplerService.convertPdfToImages(pdfBytes, format, dpi);
  }
}

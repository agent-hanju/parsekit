package me.hanju.parsekit.converter.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.hanju.parsekit.common.FileTypeDetector;
import me.hanju.parsekit.common.FileTypeDetector.FileTypeInfo;
import me.hanju.parsekit.common.exception.BadRequestException;
import me.hanju.parsekit.common.exception.UnsupportedMediaTypeException;
import me.hanju.parsekit.converter.dto.PageImageResponse;
import me.hanju.parsekit.converter.service.JodConverterService;
import me.hanju.parsekit.converter.service.MarkdownService;
import me.hanju.parsekit.converter.service.PopplerConverterService;
import me.hanju.parsekit.converter.service.PopplerConverterService.PageImage;

@Slf4j
@RestController
@RequestMapping("/api/convert")
@RequiredArgsConstructor
public class ConverterController {

  private final JodConverterService jodService;
  private final PopplerConverterService popplerService;
  private final MarkdownService markdownService;
  private final ObjectMapper objectMapper;

  @PostMapping(value = "/odt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> convertToOdt(@RequestParam("file") final MultipartFile file) {
    if (file.isEmpty()) {
      throw new BadRequestException("File is empty");
    }

    final FileTypeInfo info = FileTypeDetector.detect(file);

    final byte[] inputBytes = switch (info.category()) {
      case DOCUMENT, PLAIN_TEXT -> {
        if (".odt".equalsIgnoreCase(info.extension())) {
          throw new BadRequestException("File is already in ODT format");
        }
        yield FileTypeDetector.getBytes(file);
      }
      case MARKDOWN ->
        markdownService.convertToFullHtml(FileTypeDetector.getBytes(file), info.baseFilename());
      default ->
        throw new UnsupportedMediaTypeException(
            "Unsupported file type for ODT conversion: " + info.originalFilename());
    };

    log.info("Received ODT conversion request for file: {}", info.originalFilename());

    final byte[] convertedFile = jodService.convertToOdt(inputBytes);
    final String outputFilename = info.baseFilename() + ".odt";

    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/vnd.oasis.opendocument.text"));
    headers.setContentDispositionFormData("attachment", outputFilename);
    headers.setContentLength(convertedFile.length);

    return new ResponseEntity<>(convertedFile, headers, HttpStatus.OK);
  }

  /**
   * Convert document to PDF format
   * POST /api/convert/pdf
   *
   * Supports: HWP, HWPX, DOC, DOCX, XLS, XLSX, PPT, PPTX, ODT, ODS, ODP, and more
   */
  @PostMapping(value = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> convertToPdf(@RequestParam("file") final MultipartFile file) {
    if (file.isEmpty()) {
      throw new BadRequestException("File is empty");
    }

    final FileTypeInfo info = FileTypeDetector.detect(file);

    final byte[] inputBytes = switch (info.category()) {
      case DOCUMENT, SPREADSHEET, PRESENTATION, PLAIN_TEXT ->
        FileTypeDetector.getBytes(file);
      case MARKDOWN ->
        markdownService.convertToFullHtml(FileTypeDetector.getBytes(file), info.baseFilename());
      case PDF ->
        throw new BadRequestException("File is already in PDF format");
      default ->
        throw new UnsupportedMediaTypeException("Unsupported file type for PDF conversion: " + info.originalFilename());
    };

    log.info("Received PDF conversion request for file: {}", info.originalFilename());

    final byte[] convertedFile = jodService.convertToPdf(inputBytes);
    final String outputFilename = info.baseFilename() + ".pdf";

    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDispositionFormData("attachment", outputFilename);
    headers.setContentLength(convertedFile.length);

    return new ResponseEntity<>(convertedFile, headers, HttpStatus.OK);
  }

  /**
   * Convert PDF to images (NDJSON streaming)
   * POST /api/convert/images
   *
   * Streams images as NDJSON (one JSON object per line, per page)
   * Supports: PDF files and documents convertible to PDF
   */
  @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "application/x-ndjson")
  public ResponseEntity<StreamingResponseBody> convertToImages(
      @RequestParam("file") final MultipartFile file,
      @RequestParam(value = "format", defaultValue = "png") final String format,
      @RequestParam(value = "dpi", defaultValue = "150") final int dpi) {

    if (file.isEmpty()) {
      throw new BadRequestException("File is empty");
    }

    final FileTypeInfo info = FileTypeDetector.detect(file);

    final byte[] pdfBytes = switch (info.category()) {
      case DOCUMENT, SPREADSHEET, PRESENTATION, PLAIN_TEXT ->
        jodService.convertToPdf(FileTypeDetector.getBytes(file));
      case MARKDOWN ->
        jodService.convertToPdf(markdownService.convertToFullHtml(FileTypeDetector.getBytes(file), info.baseFilename()));
      case PDF ->
        FileTypeDetector.getBytes(file);
      default ->
        throw new UnsupportedMediaTypeException(
            "Unsupported file type for image conversion: " + info.originalFilename());
    };

    log.info("Received image conversion request for file: {} (format={}, dpi={})",
        info.originalFilename(), format, dpi);

    final StreamingResponseBody stream = outputStream -> {
      final List<PageImage> images = popplerService.convertPdfToImages(pdfBytes, format, dpi);

      for (final PageImage pageImage : images) {
        final String mimeType = "image/" + pageImage.format();
        final PageImageResponse response = new PageImageResponse(
            pageImage.page(),
            FileTypeDetector.toBase64EncodedUri(mimeType, pageImage.content()),
            pageImage.size(),
            pageImage.totalPages());

        final String jsonLine = objectMapper.writeValueAsString(response) + "\n";
        outputStream.write(jsonLine.getBytes());
        outputStream.flush();
      }

      log.info("Successfully streamed {} pages", images.size());
    };

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/x-ndjson"))
        .body(stream);
  }

  /**
   * Health check endpoint
   */
  @GetMapping("/health")
  public ResponseEntity<String> health() {
    return ResponseEntity.ok("OK");
  }
}

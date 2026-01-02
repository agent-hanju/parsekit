package me.hanju.parsekit.converter.controller;

import java.io.IOException;
import java.util.Base64;
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
import me.hanju.parsekit.converter.dto.PageImageResponse;
import me.hanju.parsekit.converter.exception.JodConverterException;
import me.hanju.parsekit.converter.exception.PopplerConverterException;
import me.hanju.parsekit.converter.service.JodConverterService;
import me.hanju.parsekit.converter.service.MarkdownService;
import me.hanju.parsekit.converter.service.PopplerConverterService;
import me.hanju.parsekit.converter.service.PopplerConverterService.PageImage;

@Slf4j
@RestController
@RequestMapping("/api/convert")
@RequiredArgsConstructor
public class ConverterController {

  private final JodConverterService jodConverter;
  private final PopplerConverterService popplerConverter;
  private final MarkdownService markdownService;
  private final ObjectMapper objectMapper;
  private final FileTypeDetector fileTypeDetector;

  /**
   * Convert document to ODT format
   * POST /api/convert/odt
   *
   * Supports: HWP, HWPX, DOC, DOCX, RTF, TXT, and other text documents
   */
  @PostMapping(value = "/odt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> convertToOdt(@RequestParam("file") MultipartFile file) {
    try {
      if (file.isEmpty()) {
        return ResponseEntity.badRequest().build();
      }

      if (!fileTypeDetector.isSupportedForOdt(file)) {
        log.warn("Unsupported file type for ODT conversion: {}", file.getOriginalFilename());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
      }

      String originalFilename = file.getOriginalFilename();
      log.info("Received ODT conversion request for file: {}", originalFilename);

      byte[] fileBytes = file.getBytes();
      String filenameForConversion = originalFilename;

      // Markdown은 HTML로 먼저 변환
      if (fileTypeDetector.isMarkdown(fileBytes, originalFilename)) {
        log.debug("Converting Markdown to HTML first");
        fileBytes = markdownService.convertToFullHtml(fileBytes, getFileNameWithoutExtension(originalFilename));
        filenameForConversion = getFileNameWithoutExtension(originalFilename) + ".html";
      }

      byte[] convertedFile = jodConverter.convertToOdt(filenameForConversion, fileBytes);
      String outputFilename = getFileNameWithoutExtension(originalFilename) + ".odt";

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.parseMediaType("application/vnd.oasis.opendocument.text"));
      headers.setContentDispositionFormData("attachment", outputFilename);
      headers.setContentLength(convertedFile.length);

      return new ResponseEntity<>(convertedFile, headers, HttpStatus.OK);

    } catch (IOException e) {
      log.error("IO error during ODT conversion", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    } catch (JodConverterException e) {
      log.error("JodConverter error during ODT conversion", e);
      return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
    } catch (Exception e) {
      log.error("Unexpected error during ODT conversion", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Convert document to PDF format
   * POST /api/convert/pdf
   *
   * Supports: HWP, HWPX, DOC, DOCX, XLS, XLSX, PPT, PPTX, ODT, ODS, ODP, and more
   */
  @PostMapping(value = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> convertToPdf(@RequestParam("file") MultipartFile file) {
    try {
      if (file.isEmpty()) {
        return ResponseEntity.badRequest().build();
      }

      if (!fileTypeDetector.isSupportedForPdf(file)) {
        log.warn("Unsupported file type for PDF conversion: {}", file.getOriginalFilename());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
      }

      String originalFilename = file.getOriginalFilename();
      log.info("Received PDF conversion request for file: {}", originalFilename);

      byte[] fileBytes = file.getBytes();
      String filenameForConversion = originalFilename;

      // Markdown은 HTML로 먼저 변환
      if (fileTypeDetector.isMarkdown(fileBytes, originalFilename)) {
        log.debug("Converting Markdown to HTML first");
        fileBytes = markdownService.convertToFullHtml(fileBytes, getFileNameWithoutExtension(originalFilename));
        filenameForConversion = getFileNameWithoutExtension(originalFilename) + ".html";
      }

      byte[] convertedFile = jodConverter.convertToPdf(filenameForConversion, fileBytes);
      String outputFilename = getFileNameWithoutExtension(originalFilename) + ".pdf";

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_PDF);
      headers.setContentDispositionFormData("attachment", outputFilename);
      headers.setContentLength(convertedFile.length);

      return new ResponseEntity<>(convertedFile, headers, HttpStatus.OK);

    } catch (IOException e) {
      log.error("IO error during PDF conversion", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    } catch (JodConverterException e) {
      log.error("JodConverter error during PDF conversion", e);
      return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
    } catch (Exception e) {
      log.error("Unexpected error during PDF conversion", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
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
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "format", defaultValue = "png") String format,
      @RequestParam(value = "dpi", defaultValue = "150") int dpi) {

    if (file.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }

    try {
      if (!fileTypeDetector.isSupportedForImages(file)) {
        log.warn("Unsupported file type for image conversion: {}", file.getOriginalFilename());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
      }
    } catch (IOException e) {
      log.error("Failed to detect file type", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    log.info("Received image conversion request for file: {} (format={}, dpi={})",
        file.getOriginalFilename(), format, dpi);

    StreamingResponseBody stream = outputStream -> {
      try {
        // Convert to PDF first if needed
        byte[] pdfBytes;
        byte[] fileBytes = file.getBytes();
        String filename = file.getOriginalFilename();

        // Markdown은 HTML로 먼저 변환
        if (fileTypeDetector.isMarkdown(fileBytes, filename)) {
          log.debug("Converting Markdown to HTML first");
          fileBytes = markdownService.convertToFullHtml(fileBytes, getFileNameWithoutExtension(filename));
          filename = getFileNameWithoutExtension(filename) + ".html";
        }

        if (fileTypeDetector.isPdf(fileBytes, filename)) {
          pdfBytes = fileBytes;
          log.debug("File is already PDF");
        } else {
          log.debug("Converting {} to PDF first", filename);
          pdfBytes = jodConverter.convertToPdf(filename, fileBytes);
        }

        // Convert PDF to images
        List<PageImage> images = popplerConverter.convertPdfToImages(pdfBytes, format, dpi);

        // Stream each page as NDJSON
        for (PageImage pageImage : images) {
          PageImageResponse response = new PageImageResponse(
              pageImage.page(),
              Base64.getEncoder().encodeToString(pageImage.content()),
              pageImage.size(),
              pageImage.totalPages());

          // Write JSON line
          String jsonLine = objectMapper.writeValueAsString(response) + "\n";
          outputStream.write(jsonLine.getBytes());
          outputStream.flush();

          log.debug("Streamed page {}/{}", pageImage.page(), pageImage.totalPages());
        }

        log.info("Successfully streamed {} pages", images.size());

      } catch (JodConverterException e) {
        log.error("JodConverter error during image conversion", e);
        throw new IOException("Document to PDF conversion failed: " + e.getMessage(), e);
      } catch (PopplerConverterException e) {
        log.error("Poppler error during image conversion", e);
        throw new IOException("PDF to image conversion failed: " + e.getMessage(), e);
      } catch (Exception e) {
        log.error("Unexpected error during image conversion", e);
        throw new IOException("Image conversion failed: " + e.getMessage(), e);
      }
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

  private String getFileNameWithoutExtension(String filename) {
    if (filename == null || filename.isEmpty()) {
      return "converted";
    }
    int lastDotIndex = filename.lastIndexOf('.');
    if (lastDotIndex > 0) {
      return filename.substring(0, lastDotIndex);
    }
    return filename;
  }
}

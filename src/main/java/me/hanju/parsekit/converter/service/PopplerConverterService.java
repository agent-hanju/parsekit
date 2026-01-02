package me.hanju.parsekit.converter.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import me.hanju.parsekit.converter.exception.PopplerConverterException;

@Slf4j
@Service
public class PopplerConverterService {

  public List<PageImage> convertPdfToImages(byte[] pdfBytes, String format, int dpi) {
    log.info("Converting PDF to images (format={}, dpi={})", format, dpi);

    Path tempPdf = null;
    Path tempDir = null;

    try {
      tempPdf = Files.createTempFile("input", ".pdf");
      tempDir = Files.createTempDirectory("pdf-images");
      Files.write(tempPdf, pdfBytes);

      int totalPages = getPdfPageCount(tempPdf);
      log.debug("PDF has {} pages", totalPages);

      List<PageImage> result = new ArrayList<>();
      String imageFormat = normalizeFormat(format);

      for (int page = 1; page <= totalPages; page++) {
        byte[] imageBytes = convertPage(tempPdf, page, imageFormat, dpi, tempDir);
        result.add(new PageImage(page, imageBytes, totalPages));
        log.debug("Converted page {}/{}", page, totalPages);
      }

      log.info("Converted PDF to {} images", result.size());
      return result;

    } catch (IOException e) {
      throw new PopplerConverterException("convertPdfToImages failed", e);
    } finally {
      cleanup(tempPdf, tempDir);
    }
  }

  private int getPdfPageCount(Path pdfPath) {
    try {
      ProcessBuilder pb = new ProcessBuilder("pdfinfo", pdfPath.toString());
      Process process = pb.start();

      String output = new String(process.getInputStream().readAllBytes());
      boolean exited = process.waitFor(30, TimeUnit.SECONDS);

      if (!exited || process.exitValue() != 0) {
        throw new IOException("pdfinfo command failed");
      }

      for (String line : output.split("\n")) {
        if (line.startsWith("Pages:")) {
          return Integer.parseInt(line.substring(6).trim());
        }
      }
      throw new IOException("Could not find page count in pdfinfo output");

    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new PopplerConverterException("getPdfPageCount failed", e);
    }
  }

  private byte[] convertPage(Path pdfPath, int page, String format, int dpi, Path outputDir) {
    try {
      String outputPrefix = outputDir.resolve("page").toString();

      List<String> command = List.of(
          "pdftoppm",
          "-" + format,
          "-r", String.valueOf(dpi),
          "-f", String.valueOf(page),
          "-l", String.valueOf(page),
          "-singlefile",
          pdfPath.toString(),
          outputPrefix);

      ProcessBuilder pb = new ProcessBuilder(command);
      Process process = pb.start();

      boolean exited = process.waitFor(60, TimeUnit.SECONDS);
      if (!exited) {
        process.destroyForcibly();
        throw new IOException("pdftoppm timed out for page " + page);
      }

      if (process.exitValue() != 0) {
        String error = new String(process.getErrorStream().readAllBytes());
        throw new IOException("pdftoppm failed for page " + page + ": " + error);
      }

      String extension = format.equals("jpeg") ? "jpg" : format;
      Path imagePath = Path.of(outputPrefix + "." + extension);

      if (!Files.exists(imagePath)) {
        throw new IOException("Output image not found: " + imagePath);
      }

      byte[] imageBytes = Files.readAllBytes(imagePath);
      Files.deleteIfExists(imagePath);
      return imageBytes;

    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new PopplerConverterException("convertPage failed for page " + page, e);
    }
  }

  private String normalizeFormat(String format) {
    return format.equalsIgnoreCase("jpg") ? "jpeg" : format.toLowerCase();
  }

  private void cleanup(Path tempPdf, Path tempDir) {
    try {
      if (tempPdf != null)
        Files.deleteIfExists(tempPdf);
      if (tempDir != null) {
        Files.walk(tempDir)
            .sorted((a, b) -> b.compareTo(a))
            .forEach(path -> {
              try {
                Files.deleteIfExists(path);
              } catch (IOException e) {
                log.warn("Failed to delete {}", path);
              }
            });
      }
    } catch (IOException e) {
      log.warn("Cleanup failed", e);
    }
  }

  public record PageImage(int page, byte[] content, int totalPages) {
    public int size() {
      return content.length;
    }
  }
}

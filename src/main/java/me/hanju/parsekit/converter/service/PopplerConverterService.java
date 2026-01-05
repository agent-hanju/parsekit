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

  public List<PageImage> convertPdfToImages(final byte[] pdfBytes, final String format, final int dpi) {
    log.info("Converting PDF to images (format={}, dpi={})", format, dpi);

    final Path tempPdf;
    final Path tempDir;

    try {
      tempPdf = Files.createTempFile("input", ".pdf");
      tempDir = Files.createTempDirectory("pdf-images");
    } catch (IOException e) {
      throw new PopplerConverterException("preprocessing failed", e);
    }

    try {
      Files.write(tempPdf, pdfBytes);

      final int totalPages = getPdfPageCount(tempPdf);
      log.debug("PDF has {} pages", totalPages);

      final List<PageImage> result = new ArrayList<>();
      final String imageFormat = format.equalsIgnoreCase("jpg") ? "jpeg" : format.toLowerCase();

      for (int page = 1; page <= totalPages; page++) {
        final byte[] imageBytes = convertPage(tempPdf, page, imageFormat, dpi, tempDir);
        result.add(new PageImage(page, imageFormat, imageBytes, totalPages));
        log.debug("Converted page {}/{}", page, totalPages);
      }

      log.info("Converted PDF to {} images", result.size());
      return result;

    } catch (final IOException e) {
      throw new PopplerConverterException("convertPdfToImages failed", e);
    } finally {
      this.cleanup(tempPdf, tempDir);
    }
  }

  private int getPdfPageCount(final Path pdfPath) {
    final ProcessBuilder pb = new ProcessBuilder("pdfinfo", pdfPath.toString());
    Process process = null;
    try {
      process = pb.start();

      final String output = new String(process.getInputStream().readAllBytes());
      final boolean exited = process.waitFor(30, TimeUnit.SECONDS);

      if (!exited) {
        throw new IOException("pdfinfo timed out");
      }

      if (process.exitValue() != 0) {
        throw new IOException("pdfinfo failed");
      }

      for (final String line : output.split("\n")) {
        if (line.startsWith("Pages:")) {
          return Integer.parseInt(line.substring(6).trim());
        }
      }
      throw new IOException("Could not find page count in pdfinfo output");

    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PopplerConverterException("getPdfPageCount failed", e);
    } catch (final IOException e) {
      throw new PopplerConverterException("getPdfPageCount failed", e);
    } finally {
      if (process != null) {
        process.destroyForcibly();
      }
    }
  }

  private byte[] convertPage(final Path pdfPath, final int page, final String format, final int dpi,
      final Path outputDir) {
    final String outputPrefix = outputDir.resolve("page").toString();

    final List<String> command = List.of(
        "pdftoppm",
        "-" + format,
        "-r", String.valueOf(dpi),
        "-f", String.valueOf(page),
        "-l", String.valueOf(page),
        "-singlefile",
        pdfPath.toString(),
        outputPrefix);

    final ProcessBuilder pb = new ProcessBuilder(command);
    Process process = null;
    try {
      process = pb.start();

      final boolean exited = process.waitFor(60, TimeUnit.SECONDS);
      if (!exited) {
        throw new IOException("pdftoppm timed out for page " + page);
      }

      if (process.exitValue() != 0) {
        final String error = new String(process.getErrorStream().readAllBytes());
        throw new IOException("pdftoppm failed for page " + page + ": " + error);
      }

      final String extension = format.equals("jpeg") ? "jpg" : format;
      final Path imagePath = Path.of(outputPrefix + "." + extension);

      if (!Files.exists(imagePath)) {
        throw new IOException("Output image not found: " + imagePath);
      }

      final byte[] imageBytes = Files.readAllBytes(imagePath);
      Files.deleteIfExists(imagePath);
      return imageBytes;

    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PopplerConverterException("convertPage failed for page " + page, e);
    } catch (final IOException e) {
      throw new PopplerConverterException("convertPage failed for page " + page, e);
    } finally {
      if (process != null) {
        process.destroyForcibly();
      }
    }
  }

  private void cleanup(final Path tempPdf, final Path tempDir) {
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
    } catch (final IOException e) {
      log.warn("Cleanup failed", e);
    }
  }

  public record PageImage(int page, String format, byte[] content, int totalPages) {
    public int size() {
      return content.length;
    }
  }
}

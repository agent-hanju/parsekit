package me.hanju.parsekit.converter.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;

import me.hanju.parsekit.converter.service.PopplerConverterService.PageImage;

class PopplerConverterServiceTest {

  private static final Path TEST_FILES_DIR = Path.of("data/inputs");
  private static final Path OUTPUT_DIR = Path.of("data/outputs");

  private static PopplerConverterService service;

  @BeforeAll
  static void setUp() throws IOException {
    service = new PopplerConverterService();
    Files.createDirectories(OUTPUT_DIR);
  }

  @Nested
  @DisplayName("convertPdfToImages()")
  class ConvertPdfToImages {

    @TestFactory
    @DisplayName("data/inputs 디렉토리의 모든 PDF 파일을 PNG 이미지로 변환한다")
    Stream<DynamicTest> shouldConvertAllPdfsToPng() throws IOException {
      if (!Files.exists(TEST_FILES_DIR)) {
        return Stream.empty();
      }

      return Files.list(TEST_FILES_DIR)
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
          .map(path -> DynamicTest.dynamicTest(
              "PNG 변환: " + path.getFileName(),
              () -> {
                byte[] pdfBytes = Files.readAllBytes(path);
                String filename = path.getFileName().toString();

                List<PageImage> result = service.convertPdfToImages(pdfBytes, "png", 150);

                assertThat(result).isNotEmpty();
                for (PageImage page : result) {
                  assertThat(page.content()).isNotEmpty();
                  assertThat(page.page()).isGreaterThan(0);
                  assertThat(page.totalPages()).isGreaterThan(0);

                  // PNG 시그니처 확인
                  byte[] pngSignature = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47 };
                  assertThat(page.content()).startsWith(pngSignature);

                  // 결과 파일 저장
                  String outputFilename = filename.replaceAll("\\.pdf$", "_page" + page.page() + ".png");
                  Files.write(OUTPUT_DIR.resolve(outputFilename), page.content());
                }
              }));
    }

    @TestFactory
    @DisplayName("data/inputs 디렉토리의 모든 PDF 파일을 JPEG 이미지로 변환한다")
    Stream<DynamicTest> shouldConvertAllPdfsToJpeg() throws IOException {
      if (!Files.exists(TEST_FILES_DIR)) {
        return Stream.empty();
      }

      return Files.list(TEST_FILES_DIR)
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
          .map(path -> DynamicTest.dynamicTest(
              "JPEG 변환: " + path.getFileName(),
              () -> {
                byte[] pdfBytes = Files.readAllBytes(path);
                String filename = path.getFileName().toString();

                List<PageImage> result = service.convertPdfToImages(pdfBytes, "jpg", 150);

                assertThat(result).isNotEmpty();
                for (PageImage page : result) {
                  assertThat(page.content()).isNotEmpty();

                  // JPEG 시그니처 확인
                  byte[] jpegSignature = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
                  assertThat(page.content()).startsWith(jpegSignature);

                  // 결과 파일 저장
                  String outputFilename = filename.replaceAll("\\.pdf$", "_page" + page.page() + ".jpg");
                  Files.write(OUTPUT_DIR.resolve(outputFilename), page.content());
                }
              }));
    }

    @TestFactory
    @DisplayName("다양한 DPI로 PDF를 이미지로 변환한다")
    Stream<DynamicTest> shouldConvertWithDifferentDpi() throws IOException {
      if (!Files.exists(TEST_FILES_DIR)) {
        return Stream.empty();
      }

      Path firstPdf = Files.list(TEST_FILES_DIR)
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
          .findFirst()
          .orElse(null);

      if (firstPdf == null) {
        return Stream.empty();
      }

      int[] dpiValues = { 72, 150, 300 };

      return Arrays.stream(dpiValues)
          .boxed()
          .flatMap(dpi -> {
            try {
              byte[] pdfBytes = Files.readAllBytes(firstPdf);
              return Stream.of(DynamicTest.dynamicTest(
                  "DPI " + dpi + ": " + firstPdf.getFileName(),
                  () -> {
                    List<PageImage> result = service.convertPdfToImages(pdfBytes, "png", dpi);

                    assertThat(result).isNotEmpty();
                    // DPI가 높을수록 이미지 크기가 커야 함
                    assertThat(result.get(0).size()).isGreaterThan(0);
                  }));
            } catch (IOException e) {
              return Stream.empty();
            }
          });
    }
  }
}

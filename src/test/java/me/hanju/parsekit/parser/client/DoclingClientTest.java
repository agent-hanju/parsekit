package me.hanju.parsekit.parser.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import me.hanju.parsekit.parser.dto.ParseResult;

@SpringBootTest
@ActiveProfiles("test")
class DoclingClientTest {

  private static final Path TEST_FILES_DIR = Path.of("data/inputs");
  private static final Path OUTPUT_DIR = Path.of("data/outputs");

  @Autowired
  private DoclingClient client;

  @BeforeAll
  static void setUp() throws IOException {
    Files.createDirectories(OUTPUT_DIR);
  }

  @Nested
  @DisplayName("parse()")
  class Parse {

    @TestFactory
    @DisplayName("data/inputs 디렉토리의 모든 파일을 파싱한다")
    Stream<DynamicTest> shouldParseAllTestFiles() throws IOException {
      if (!Files.exists(TEST_FILES_DIR)) {
        return Stream.empty();
      }

      return Files.list(TEST_FILES_DIR)
          .filter(Files::isRegularFile)
          .filter(path -> isDoclingSupported(path.getFileName().toString()))
          .map(path -> DynamicTest.dynamicTest(
              "파싱: " + path.getFileName(),
              () -> {
                byte[] fileBytes = Files.readAllBytes(path);
                String filename = path.getFileName().toString();

                ParseResult result = client.parse(fileBytes, filename);

                assertThat(result.filename()).isEqualTo(filename);
                assertThat(result.markdown()).isNotBlank();
              }));
    }

    @TestFactory
    @DisplayName("data/inputs 디렉토리의 모든 파일을 embedded 모드로 파싱한다")
    Stream<DynamicTest> shouldParseAllTestFilesWithEmbeddedMode() throws IOException {
      if (!Files.exists(TEST_FILES_DIR)) {
        return Stream.empty();
      }

      return Files.list(TEST_FILES_DIR)
          .filter(Files::isRegularFile)
          .filter(path -> isDoclingSupported(path.getFileName().toString()))
          .map(path -> DynamicTest.dynamicTest(
              "파싱 (embedded): " + path.getFileName(),
              () -> {
                byte[] fileBytes = Files.readAllBytes(path);
                String filename = path.getFileName().toString();

                ParseResult result = client.parse(fileBytes, filename, "embedded");

                assertThat(result.filename()).isEqualTo(filename);
                assertThat(result.markdown()).isNotBlank();
              }));
    }
  }

  /**
   * Docling이 지원하는 파일 형식인지 확인
   * 지원: pdf, docx, xlsx, pptx, md, html, csv, image (jpg, jpeg)
   * 미지원: hwp, png (단독 이미지)
   */
  private static boolean isDoclingSupported(String filename) {
    String lower = filename.toLowerCase();
    return lower.endsWith(".pdf") || lower.endsWith(".docx")
        || lower.endsWith(".xlsx") || lower.endsWith(".pptx")
        || lower.endsWith(".md") || lower.endsWith(".html")
        || lower.endsWith(".csv") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
  }
}

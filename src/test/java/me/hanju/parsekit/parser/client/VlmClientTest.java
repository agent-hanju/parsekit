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

@SpringBootTest
@ActiveProfiles("test")
class VlmClientTest {

  private static final Path TEST_FILES_DIR = Path.of("data/inputs");
  private static final Path OUTPUT_DIR = Path.of("data/outputs");

  @Autowired
  private VlmClient client;

  @BeforeAll
  static void setUp() throws IOException {
    Files.createDirectories(OUTPUT_DIR);
  }

  @Nested
  @DisplayName("ocr()")
  class Ocr {

    @TestFactory
    @DisplayName("data/inputs 디렉토리의 모든 이미지 파일을 OCR한다")
    Stream<DynamicTest> shouldOcrAllImageFiles() throws IOException {
      if (!Files.exists(TEST_FILES_DIR)) {
        return Stream.empty();
      }

      return Files.list(TEST_FILES_DIR)
          .filter(Files::isRegularFile)
          .filter(path -> isImageFile(path.getFileName().toString()))
          .map(path -> DynamicTest.dynamicTest(
              "OCR: " + path.getFileName(),
              () -> {
                byte[] imageBytes = Files.readAllBytes(path);

                String result = client.ocr(imageBytes);

                assertThat(result).isNotBlank();
              }));
    }

    @TestFactory
    @DisplayName("커스텀 프롬프트로 모든 이미지 파일을 OCR한다")
    Stream<DynamicTest> shouldOcrWithCustomPrompt() throws IOException {
      if (!Files.exists(TEST_FILES_DIR)) {
        return Stream.empty();
      }

      String customPrompt = "Describe this image in detail, including any text, diagrams, or visual elements.";

      return Files.list(TEST_FILES_DIR)
          .filter(Files::isRegularFile)
          .filter(path -> isImageFile(path.getFileName().toString()))
          .map(path -> DynamicTest.dynamicTest(
              "OCR (custom prompt): " + path.getFileName(),
              () -> {
                byte[] imageBytes = Files.readAllBytes(path);

                String result = client.ocr(imageBytes, customPrompt);

                assertThat(result).isNotBlank();
              }));
    }
  }

  private static boolean isImageFile(String filename) {
    String lower = filename.toLowerCase();
    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
        || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp");
  }
}

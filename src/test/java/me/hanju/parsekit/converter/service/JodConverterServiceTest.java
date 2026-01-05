package me.hanju.parsekit.converter.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.jodconverter.core.DocumentConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JodConverterServiceTest {

  private static final Path TEST_FILES_DIR = Path.of("data/inputs");
  private static final Path OUTPUT_DIR = Path.of("data/outputs");

  @Autowired
  private DocumentConverter documentConverter;

  private static JodConverterService service;

  @BeforeAll
  static void setUp(@Autowired DocumentConverter documentConverter) throws IOException {
    service = new JodConverterService(documentConverter);
    Files.createDirectories(OUTPUT_DIR);
  }

  @Nested
  @DisplayName("convertToOdt()")
  class ConvertToOdt {

    @TestFactory
    @DisplayName("data/inputs 디렉토리의 모든 문서 파일을 ODT로 변환한다")
    Stream<DynamicTest> shouldConvertAllDocumentsToOdt() throws IOException {
      if (!Files.exists(TEST_FILES_DIR)) {
        return Stream.empty();
      }

      return Files.list(TEST_FILES_DIR)
          .filter(Files::isRegularFile)
          .filter(path -> isTextDocumentFile(path.getFileName().toString()))
          .map(path -> DynamicTest.dynamicTest(
              "ODT 변환: " + path.getFileName(),
              () -> {
                byte[] fileBytes = Files.readAllBytes(path);
                String filename = path.getFileName().toString();

                byte[] result = service.convertToOdt(fileBytes);

                assertThat(result).isNotEmpty();
                assertThat(result.length).isGreaterThan(0);

                // 결과 파일 저장
                String outputFilename = filename.replaceAll("\\.[^.]+$", ".odt");
                Files.write(OUTPUT_DIR.resolve(outputFilename), result);
              }));
    }
  }

  @Nested
  @DisplayName("convertToPdf()")
  class ConvertToPdf {

    @TestFactory
    @DisplayName("data/inputs 디렉토리의 모든 문서 파일을 PDF로 변환한다")
    Stream<DynamicTest> shouldConvertAllDocumentsToPdf() throws IOException {
      if (!Files.exists(TEST_FILES_DIR)) {
        return Stream.empty();
      }

      return Files.list(TEST_FILES_DIR)
          .filter(Files::isRegularFile)
          .filter(path -> isDocumentFile(path.getFileName().toString()))
          .map(path -> DynamicTest.dynamicTest(
              "PDF 변환: " + path.getFileName(),
              () -> {
                byte[] fileBytes = Files.readAllBytes(path);
                String filename = path.getFileName().toString();

                byte[] result = service.convertToPdf(fileBytes);

                assertThat(result).isNotEmpty();
                // PDF 시그니처 확인
                assertThat(new String(result, 0, 4)).isEqualTo("%PDF");

                // 결과 파일 저장
                String outputFilename = filename.replaceAll("\\.[^.]+$", ".pdf");
                Files.write(OUTPUT_DIR.resolve(outputFilename), result);
              }));
    }
  }

  private static boolean isDocumentFile(String filename) {
    String lower = filename.toLowerCase();
    return lower.endsWith(".doc") || lower.endsWith(".docx")
        || lower.endsWith(".xls") || lower.endsWith(".xlsx")
        || lower.endsWith(".ppt") || lower.endsWith(".pptx")
        || lower.endsWith(".odt") || lower.endsWith(".ods") || lower.endsWith(".odp")
        || lower.endsWith(".rtf") || lower.endsWith(".txt")
        || lower.endsWith(".hwp") || lower.endsWith(".hwpx");
  }

  private static boolean isTextDocumentFile(String filename) {
    String lower = filename.toLowerCase();
    return lower.endsWith(".doc") || lower.endsWith(".docx")
        || lower.endsWith(".odt") || lower.endsWith(".rtf") || lower.endsWith(".txt")
        || lower.endsWith(".hwp") || lower.endsWith(".hwpx");
  }
}

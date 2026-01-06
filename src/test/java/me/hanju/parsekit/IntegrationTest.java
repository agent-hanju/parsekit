package me.hanju.parsekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.jodconverter.core.DocumentConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import me.hanju.parsekit.common.FileTypeDetector;
import me.hanju.parsekit.common.exception.UnsupportedMediaTypeException;
import me.hanju.parsekit.converter.service.JodConverterService;
import me.hanju.parsekit.converter.service.MarkdownService;
import me.hanju.parsekit.converter.service.PopplerConverterService;
import me.hanju.parsekit.parser.client.DoclingClient;
import me.hanju.parsekit.parser.client.VlmClient;
import me.hanju.parsekit.parser.config.ParserProperties;
import me.hanju.parsekit.parser.controller.DoclingParserController;
import me.hanju.parsekit.parser.controller.HybridParserController;
import me.hanju.parsekit.parser.controller.VlmParserController;
import me.hanju.parsekit.parser.dto.ParseResult;

/**
 * 통합 테스트 - 모든 SpringBootTest를 하나의 Context에서 실행
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntegrationTest {

  private static final Path TEST_FILES_DIR = Path.of("data/inputs");
  private static final Path OUTPUT_DIR = Path.of("data/outputs");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private DocumentConverter documentConverter;

  @Autowired
  private DoclingClient doclingClient;

  @Autowired
  private VlmClient vlmClient;

  @Autowired
  private JodConverterService jodConverter;

  @Autowired
  private MarkdownService markdownService;

  @Autowired
  private PopplerConverterService popplerConverter;

  @Autowired
  private ParserProperties parserProperties;

  @Autowired
  private ObjectMapper objectMapper;

  @BeforeAll
  static void setUp() throws IOException {
    Files.createDirectories(OUTPUT_DIR);
  }

  // ============================================
  // Converter Controller Tests
  // ============================================

  @Nested
  @DisplayName("ConverterController")
  class ConverterControllerTests {

    @Nested
    @DisplayName("GET /api/convert/health")
    class Health {

      @Test
      @DisplayName("헬스체크가 OK를 반환한다")
      void shouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/convert/health"))
            .andExpect(status().isOk())
            .andExpect(content().string("OK"));
      }
    }

    @Nested
    @DisplayName("POST /api/convert/odt")
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

                  MockMultipartFile file = new MockMultipartFile(
                      "file", filename, "application/octet-stream", fileBytes);

                  MvcResult result = mockMvc.perform(multipart("/api/convert/odt").file(file))
                      .andExpect(status().isOk())
                      .andExpect(header().string("Content-Type", "application/vnd.oasis.opendocument.text"))
                      .andReturn();

                  byte[] content = result.getResponse().getContentAsByteArray();
                  assertThat(content).isNotEmpty();

                  String outputFilename = filename.replaceAll("\\.[^.]+$", "_controller.odt");
                  Files.write(OUTPUT_DIR.resolve(outputFilename), content);
                }));
      }
    }

    @Nested
    @DisplayName("POST /api/convert/pdf")
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

                  MockMultipartFile file = new MockMultipartFile(
                      "file", filename, "application/octet-stream", fileBytes);

                  MvcResult result = mockMvc.perform(multipart("/api/convert/pdf").file(file))
                      .andExpect(status().isOk())
                      .andExpect(header().string("Content-Type", "application/pdf"))
                      .andReturn();

                  byte[] content = result.getResponse().getContentAsByteArray();
                  assertThat(content).isNotEmpty();
                  assertThat(new String(content, 0, 4)).isEqualTo("%PDF");

                  String outputFilename = filename.replaceAll("\\.[^.]+$", "_controller.pdf");
                  Files.write(OUTPUT_DIR.resolve(outputFilename), content);
                }));
      }
    }

    @Nested
    @DisplayName("POST /api/convert/images")
    class ConvertToImages {

      @TestFactory
      @DisplayName("data/inputs 디렉토리의 모든 PDF 파일을 이미지로 변환한다 (NDJSON)")
      Stream<DynamicTest> shouldConvertAllPdfsToImages() throws IOException {
        if (!Files.exists(TEST_FILES_DIR)) {
          return Stream.empty();
        }

        return Files.list(TEST_FILES_DIR)
            .filter(Files::isRegularFile)
            .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
            .map(path -> DynamicTest.dynamicTest(
                "이미지 변환: " + path.getFileName(),
                () -> {
                  byte[] fileBytes = Files.readAllBytes(path);
                  String filename = path.getFileName().toString();

                  MockMultipartFile file = new MockMultipartFile(
                      "file", filename, "application/pdf", fileBytes);

                  MvcResult asyncResult = mockMvc.perform(multipart("/api/convert/images")
                      .file(file)
                      .param("format", "png")
                      .param("dpi", "150"))
                      .andExpect(request().asyncStarted())
                      .andReturn();

                  MvcResult result = mockMvc.perform(asyncDispatch(asyncResult))
                      .andExpect(status().isOk())
                      .andExpect(header().string("Content-Type", "application/x-ndjson"))
                      .andReturn();

                  String content = result.getResponse().getContentAsString();
                  assertThat(content).isNotBlank();

                  String[] lines = content.split("\n");
                  assertThat(lines.length).isGreaterThan(0);
                  for (String line : lines) {
                    assertThat(line).contains("\"page\":");
                    assertThat(line).contains("\"encoded_uri\":");
                    assertThat(line).contains("\"total_pages\":");
                  }
                }));
      }
    }
  }

  // ============================================
  // JodConverter Service Tests
  // ============================================

  @Nested
  @DisplayName("JodConverterService")
  class JodConverterServiceTests {

    @Nested
    @DisplayName("convertToOdt()")
    class ConvertToOdt {

      @TestFactory
      @DisplayName("data/inputs 디렉토리의 모든 문서 파일을 ODT로 변환한다")
      Stream<DynamicTest> shouldConvertAllDocumentsToOdt() throws IOException {
        if (!Files.exists(TEST_FILES_DIR)) {
          return Stream.empty();
        }

        JodConverterService service = new JodConverterService(documentConverter);

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

        JodConverterService service = new JodConverterService(documentConverter);

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
                  assertThat(new String(result, 0, 4)).isEqualTo("%PDF");

                  String outputFilename = filename.replaceAll("\\.[^.]+$", ".pdf");
                  Files.write(OUTPUT_DIR.resolve(outputFilename), result);
                }));
      }
    }
  }

  // ============================================
  // Docling Client Tests
  // ============================================

  @Nested
  @DisplayName("DoclingClient")
  class DoclingClientTests {

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

                  ParseResult result = doclingClient.parse(fileBytes, filename);

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

                  ParseResult result = doclingClient.parse(fileBytes, filename, "embedded");

                  assertThat(result.filename()).isEqualTo(filename);
                  assertThat(result.markdown()).isNotBlank();
                }));
      }
    }
  }

  // ============================================
  // VLM Client Tests
  // ============================================

  @Nested
  @DisplayName("VlmClient")
  class VlmClientTests {

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
                  String filename = path.getFileName().toString();
                  MockMultipartFile file = new MockMultipartFile(
                      "file", filename, "application/octet-stream", imageBytes);
                  String encodedUri = FileTypeDetector.toBase64EncodedUri(file);

                  String result = vlmClient.ocr(encodedUri);

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
                  String filename = path.getFileName().toString();
                  MockMultipartFile file = new MockMultipartFile(
                      "file", filename, "application/octet-stream", imageBytes);
                  String encodedUri = FileTypeDetector.toBase64EncodedUri(file);

                  String result = vlmClient.ocr(encodedUri, customPrompt);

                  assertThat(result).isNotBlank();
                }));
      }
    }
  }

  // ============================================
  // Docling Parser Controller Tests
  // ============================================

  @Nested
  @DisplayName("DoclingParserController")
  class DoclingParserControllerTests {

    private DoclingParserController createController() {
      return new DoclingParserController(doclingClient, jodConverter);
    }

    @Nested
    @DisplayName("parse()")
    class Parse {

      @TestFactory
      @DisplayName("data/inputs 디렉토리의 모든 문서 파일을 파싱한다")
      Stream<DynamicTest> shouldParseAllDocuments() throws IOException {
        if (!Files.exists(TEST_FILES_DIR)) {
          return Stream.empty();
        }

        DoclingParserController controller = createController();

        return Files.list(TEST_FILES_DIR)
            .filter(Files::isRegularFile)
            .filter(path -> isDoclingControllerSupported(path.getFileName().toString()))
            .map(path -> DynamicTest.dynamicTest(
                "파싱: " + path.getFileName(),
                () -> {
                  byte[] fileBytes = Files.readAllBytes(path);
                  String filename = path.getFileName().toString();

                  MockMultipartFile file = new MockMultipartFile(
                      "file", filename, "application/octet-stream", fileBytes);

                  ResponseEntity<ParseResult> response = controller.parse(file);

                  assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                  assertThat(response.getBody()).isNotNull();
                  assertThat(response.getBody().filename()).isEqualTo(filename);
                  assertThat(response.getBody().markdown()).isNotBlank();

                  String outputFilename = filename.replaceAll("\\.[^.]+$", "_docling.json");
                  Files.writeString(OUTPUT_DIR.resolve(outputFilename),
                      objectMapper.writeValueAsString(response.getBody()));
                }));
      }

      @TestFactory
      @DisplayName("data/inputs 디렉토리의 Docling 지원 이미지를 파싱한다")
      Stream<DynamicTest> shouldParseDoclingImages() throws IOException {
        if (!Files.exists(TEST_FILES_DIR)) {
          return Stream.empty();
        }

        DoclingParserController controller = createController();

        return Files.list(TEST_FILES_DIR)
            .filter(Files::isRegularFile)
            .filter(path -> isDoclingImage(path.getFileName().toString()))
            .map(path -> DynamicTest.dynamicTest(
                "이미지 파싱: " + path.getFileName(),
                () -> {
                  byte[] fileBytes = Files.readAllBytes(path);
                  String filename = path.getFileName().toString();

                  MockMultipartFile file = new MockMultipartFile(
                      "file", filename, "application/octet-stream", fileBytes);

                  ResponseEntity<ParseResult> response = controller.parse(file);

                  assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                  assertThat(response.getBody()).isNotNull();
                  assertThat(response.getBody().filename()).isEqualTo(filename);

                  String outputFilename = filename.replaceAll("\\.[^.]+$", "_docling_image.json");
                  Files.writeString(OUTPUT_DIR.resolve(outputFilename),
                      objectMapper.writeValueAsString(response.getBody()));
                }));
      }

      @Test
      @DisplayName("플레인 텍스트 파일은 UnsupportedMediaTypeException을 던진다")
      void shouldThrowForPlainText() {
        DoclingParserController controller = createController();
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.txt", "text/plain", "Hello World".getBytes());

        assertThatThrownBy(() -> controller.parse(file))
            .isInstanceOf(UnsupportedMediaTypeException.class)
            .hasMessageContaining("Plain text files not supported");
      }

      @Test
      @DisplayName("지원하지 않는 이미지 타입은 UnsupportedMediaTypeException을 던진다")
      void shouldThrowForUnsupportedImageType() {
        DoclingParserController controller = createController();
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.svg", "image/svg+xml", "<svg></svg>".getBytes());

        assertThatThrownBy(() -> controller.parse(file))
            .isInstanceOf(UnsupportedMediaTypeException.class);
      }
    }
  }

  // ============================================
  // Hybrid Parser Controller Tests
  // ============================================

  @Nested
  @DisplayName("HybridParserController")
  class HybridParserControllerTests {

    private HybridParserController createController() {
      return new HybridParserController(doclingClient, vlmClient, jodConverter, parserProperties);
    }

    @Nested
    @DisplayName("parse()")
    class Parse {

      @TestFactory
      @DisplayName("data/inputs 디렉토리의 모든 문서 파일을 하이브리드 파싱한다")
      Stream<DynamicTest> shouldParseAllDocuments() throws IOException {
        if (!Files.exists(TEST_FILES_DIR)) {
          return Stream.empty();
        }

        HybridParserController controller = createController();

        return Files.list(TEST_FILES_DIR)
            .filter(Files::isRegularFile)
            .filter(path -> isHybridDocumentFile(path.getFileName().toString()))
            .map(path -> DynamicTest.dynamicTest(
                "하이브리드 파싱: " + path.getFileName(),
                () -> {
                  byte[] fileBytes = Files.readAllBytes(path);
                  String filename = path.getFileName().toString();

                  MockMultipartFile file = new MockMultipartFile(
                      "file", filename, "application/octet-stream", fileBytes);

                  ResponseEntity<ParseResult> response = controller.parse(file, 150);

                  assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                  assertThat(response.getBody()).isNotNull();
                  assertThat(response.getBody().filename()).isEqualTo(filename);
                  assertThat(response.getBody().markdown()).isNotBlank();

                  String outputFilename = filename.replaceAll("\\.[^.]+$", "_hybrid.json");
                  Files.writeString(OUTPUT_DIR.resolve(outputFilename),
                      objectMapper.writeValueAsString(response.getBody()));
                }));
      }

      @TestFactory
      @DisplayName("data/inputs 디렉토리의 모든 이미지 파일을 VLM으로 직접 OCR한다")
      Stream<DynamicTest> shouldOcrAllImages() throws IOException {
        if (!Files.exists(TEST_FILES_DIR)) {
          return Stream.empty();
        }

        HybridParserController controller = createController();

        return Files.list(TEST_FILES_DIR)
            .filter(Files::isRegularFile)
            .filter(path -> isImageFile(path.getFileName().toString()))
            .map(path -> DynamicTest.dynamicTest(
                "이미지 VLM OCR: " + path.getFileName(),
                () -> {
                  byte[] fileBytes = Files.readAllBytes(path);
                  String filename = path.getFileName().toString();

                  MockMultipartFile file = new MockMultipartFile(
                      "file", filename, "application/octet-stream", fileBytes);

                  ResponseEntity<ParseResult> response = controller.parse(file, 150);

                  assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                  assertThat(response.getBody()).isNotNull();
                  assertThat(response.getBody().filename()).isEqualTo(filename);

                  String outputFilename = filename.replaceAll("\\.[^.]+$", "_hybrid_ocr.json");
                  Files.writeString(OUTPUT_DIR.resolve(outputFilename),
                      objectMapper.writeValueAsString(response.getBody()));
                }));
      }

      @Test
      @DisplayName("플레인 텍스트 파일은 UnsupportedMediaTypeException을 던진다")
      void shouldThrowForPlainText() {
        HybridParserController controller = createController();
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.txt", "text/plain", "Hello World".getBytes());

        assertThatThrownBy(() -> controller.parse(file, 150))
            .isInstanceOf(UnsupportedMediaTypeException.class)
            .hasMessageContaining("Plain text files not supported");
      }
    }
  }

  // ============================================
  // VLM Parser Controller Tests
  // ============================================

  @Nested
  @DisplayName("VlmParserController")
  class VlmParserControllerTests {

    private VlmParserController createController() {
      return new VlmParserController(vlmClient, markdownService, jodConverter, popplerConverter, parserProperties);
    }

    @Nested
    @DisplayName("parse()")
    class Parse {

      @TestFactory
      @DisplayName("data/inputs 디렉토리의 모든 이미지 파일을 OCR한다")
      Stream<DynamicTest> shouldOcrAllImages() throws IOException {
        if (!Files.exists(TEST_FILES_DIR)) {
          return Stream.empty();
        }

        VlmParserController controller = createController();

        return Files.list(TEST_FILES_DIR)
            .filter(Files::isRegularFile)
            .filter(path -> isImageFile(path.getFileName().toString()))
            .map(path -> DynamicTest.dynamicTest(
                "OCR: " + path.getFileName(),
                () -> {
                  byte[] fileBytes = Files.readAllBytes(path);
                  String filename = path.getFileName().toString();

                  MockMultipartFile file = new MockMultipartFile(
                      "file", filename, "application/octet-stream", fileBytes);

                  ResponseEntity<ParseResult> response = controller.parse(file, 150);

                  assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                  assertThat(response.getBody()).isNotNull();
                  assertThat(response.getBody().filename()).isEqualTo(filename);

                  String outputFilename = filename.replaceAll("\\.[^.]+$", "_vlm.json");
                  Files.writeString(OUTPUT_DIR.resolve(outputFilename),
                      objectMapper.writeValueAsString(response.getBody()));
                }));
      }

      @TestFactory
      @DisplayName("data/inputs 디렉토리의 모든 PDF 파일을 OCR한다")
      Stream<DynamicTest> shouldOcrAllPdfs() throws IOException {
        if (!Files.exists(TEST_FILES_DIR)) {
          return Stream.empty();
        }

        VlmParserController controller = createController();

        return Files.list(TEST_FILES_DIR)
            .filter(Files::isRegularFile)
            .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
            .map(path -> DynamicTest.dynamicTest(
                "PDF OCR: " + path.getFileName(),
                () -> {
                  byte[] fileBytes = Files.readAllBytes(path);
                  String filename = path.getFileName().toString();

                  MockMultipartFile file = new MockMultipartFile(
                      "file", filename, "application/pdf", fileBytes);

                  ResponseEntity<ParseResult> response = controller.parse(file, 150);

                  assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
                  assertThat(response.getBody()).isNotNull();
                  assertThat(response.getBody().filename()).isEqualTo(filename);

                  String outputFilename = filename.replaceAll("\\.[^.]+$", "_vlm_ocr.json");
                  Files.writeString(OUTPUT_DIR.resolve(outputFilename),
                      objectMapper.writeValueAsString(response.getBody()));
                }));
      }

      @Test
      @DisplayName("플레인 텍스트 파일은 UnsupportedMediaTypeException을 던진다")
      void shouldThrowForPlainText() {
        VlmParserController controller = createController();
        MockMultipartFile file = new MockMultipartFile(
            "file", "test.txt", "text/plain", "Hello World".getBytes());

        assertThatThrownBy(() -> controller.parse(file, 150))
            .isInstanceOf(UnsupportedMediaTypeException.class)
            .hasMessageContaining("Plain text files not supported");
      }
    }
  }

  // ============================================
  // Helper Methods
  // ============================================

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

  private static boolean isImageFile(String filename) {
    String lower = filename.toLowerCase();
    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
        || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp");
  }

  private static boolean isDoclingSupported(String filename) {
    String lower = filename.toLowerCase();
    return lower.endsWith(".pdf") || lower.endsWith(".docx")
        || lower.endsWith(".xlsx") || lower.endsWith(".pptx")
        || lower.endsWith(".md") || lower.endsWith(".html")
        || lower.endsWith(".csv") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
  }

  private static boolean isDoclingControllerSupported(String filename) {
    String lower = filename.toLowerCase();
    return lower.endsWith(".pdf") || lower.endsWith(".docx")
        || lower.endsWith(".xlsx") || lower.endsWith(".pptx")
        || lower.endsWith(".hwp");
  }

  private static boolean isDoclingImage(String filename) {
    String lower = filename.toLowerCase();
    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
  }

  private static boolean isHybridDocumentFile(String filename) {
    String lower = filename.toLowerCase();
    return lower.endsWith(".pdf") || lower.endsWith(".docx")
        || lower.endsWith(".xlsx") || lower.endsWith(".pptx")
        || lower.endsWith(".hwp") || lower.endsWith(".hwpx")
        || lower.endsWith(".doc") || lower.endsWith(".xls") || lower.endsWith(".ppt");
  }
}

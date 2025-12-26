package me.hanju.parsekit.scenario;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import me.hanju.parsekit.ConverterClient;
import me.hanju.parsekit.docling.DoclingClient;
import me.hanju.parsekit.payload.ConvertResult;
import me.hanju.parsekit.payload.ImageConvertResult;
import me.hanju.parsekit.payload.ImagePage;
import me.hanju.parsekit.payload.ParseResult;
import me.hanju.parsekit.vlm.VlmClient;

/**
 * 시나리오 통합 테스트
 *
 * 시나리오 1: 문서 → PDF → Docling(텍스트) + 이미지→VLM(OCR) 취합
 * 시나리오 2: 문서 → 이미지 → VLM 직접 처리
 *
 * 테스트 대상 파일은 inputDir 또는 classpath에서 동적으로 검색됩니다.
 */
class ScenarioTest {

  private static final Logger log = LoggerFactory.getLogger(ScenarioTest.class);

  private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("hwp", "docx", "pptx", "xlsx", "doc", "ppt", "xls");

  private static final Map<String, String> CONTENT_TYPE_MAP = Map.of(
      "hwp", "application/x-hwp",
      "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
      "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      "doc", "application/msword",
      "ppt", "application/vnd.ms-powerpoint",
      "xls", "application/vnd.ms-excel"
  );

  private static Path inputDir;
  private static Path outputDir;
  private static int testDpi;

  private static ConverterClient converterClient;
  private static DoclingClient doclingClient;
  private static VlmClient vlmClient;

  @BeforeAll
  static void setUp() throws IOException {
    final String converterUrl = resolveProperty("parsekit.converter.url");
    final String doclingUrl = resolveProperty("parsekit.docling.url");
    final String vlmUrl = resolveProperty("parsekit.vlm.url");
    final String vlmModel = resolveProperty("parsekit.vlm.model");
    final String inputDirValue = resolveProperty("parsekit.test.input-dir");
    final String outputDirValue = resolveProperty("parsekit.test.output-dir");
    final String dpiValue = resolveProperty("parsekit.test.dpi");

    inputDir = Paths.get(inputDirValue);
    outputDir = Paths.get(outputDirValue);
    testDpi = (dpiValue == null || dpiValue.isBlank()) ? 72 : Integer.parseInt(dpiValue);

    converterClient = new ConverterClient(WebClient.builder(), converterUrl);
    doclingClient = new DoclingClient(WebClient.builder(), doclingUrl);
    vlmClient = new VlmClient(WebClient.builder(), vlmUrl, vlmModel);
    Files.createDirectories(outputDir);
  }

  private static String resolveProperty(final String key) throws IOException {
    final Properties props = new Properties();
    try (InputStream is = ScenarioTest.class.getClassLoader().getResourceAsStream("application.properties")) {
      props.load(is);
    }
    final String value = props.getProperty(key);
    if (value != null && value.startsWith("${") && value.contains(":")) {
      final String envKey = value.substring(2, value.indexOf(':'));
      final String defaultValue = value.substring(value.indexOf(':') + 1, value.length() - 1);
      // 시스템 프로퍼티 → 환경 변수 → 기본값 순서로 확인
      final String sysProp = System.getProperty(envKey);
      if (sysProp != null && !sysProp.isBlank()) {
        return sysProp;
      }
      final String envValue = System.getenv(envKey);
      return envValue != null ? envValue : defaultValue;
    }
    return value;
  }

  /**
   * 테스트 대상 문서 파일 목록을 동적으로 검색
   */
  static Stream<String> documentFiles() throws IOException {
    final String inputDirValue = resolveProperty("parsekit.test.input-dir");
    final Path localInputDir = Paths.get(inputDirValue);

    if (!Files.isDirectory(localInputDir)) {
      throw new IOException("입력 디렉토리가 존재하지 않음: " + localInputDir.toAbsolutePath());
    }

    final List<String> files = new ArrayList<>();
    try (Stream<Path> paths = Files.list(localInputDir)) {
      paths.filter(Files::isRegularFile)
          .map(Path::getFileName)
          .map(Path::toString)
          .filter(ScenarioTest::isSupportedFile)
          .forEach(files::add);
    }

    if (files.isEmpty()) {
      throw new IOException("테스트 대상 문서 파일이 없음: " + localInputDir.toAbsolutePath());
    }

    log.info("테스트 대상 문서 파일: {}", files);
    Collections.sort(files);
    return files.stream();
  }

  private static boolean isSupportedFile(final String filename) {
    final String ext = getExtension(filename).toLowerCase();
    return SUPPORTED_EXTENSIONS.contains(ext);
  }

  private static String getExtension(final String filename) {
    final int dotIndex = filename.lastIndexOf('.');
    return dotIndex > 0 ? filename.substring(dotIndex + 1) : "";
  }

  private static String getContentType(final String filename) {
    final String ext = getExtension(filename).toLowerCase();
    return CONTENT_TYPE_MAP.getOrDefault(ext, "application/octet-stream");
  }

  // ============================================================
  // 시나리오 1: PDF → Docling → 이미지만 VLM 취합
  // ============================================================

  @ParameterizedTest(name = "시나리오1: {0}")
  @MethodSource("documentFiles")
  @DisplayName("시나리오1: 문서 → PDF/Docling + 이미지/VLM 취합")
  void scenario1(final String filename) throws IOException {
    assumeAllServicesAvailable();

    final byte[] content = loadInput(filename);
    final String contentType = getContentType(filename);
    final String result = executeScenario1(content, filename, contentType);

    assertNotNull(result);
    assertFalse(result.isBlank());
    saveOutput("scenario1_" + getBaseName(filename) + ".md", result);
    System.out.println("시나리오1 " + filename + " 결과:\n" + truncate(result, 1000));
  }

  /**
   * 시나리오 1 실행:
   * 1. 문서 → PDF 변환
   * 2. Docling으로 텍스트 추출 (이미지는 base64 임베드 모드)
   * 3. 마크다운에서 base64 이미지 추출
   * 4. 각 이미지를 VLM으로 OCR
   * 5. base64 이미지를 VLM 결과로 대체
   */
  private String executeScenario1(final byte[] content, final String filename, final String contentType)
      throws IOException {
    // 1. 문서 → PDF 변환
    final ConvertResult pdfResult = converterClient.convert(content, filename, contentType);
    assertTrue(pdfResult.converted());
    saveOutputBytes("scenario1_" + getBaseName(filename) + ".pdf", pdfResult.content());

    // 2. Docling으로 텍스트 추출 (이미지는 base64 임베드 모드)
    final ParseResult doclingResult = doclingClient.parse(pdfResult.content(), pdfResult.filename());
    String markdown = doclingResult.asMarkdown();

    // 3. 마크다운에서 base64 이미지 추출 및 VLM 처리
    markdown = processBase64Images(markdown);

    return markdown;
  }

  /**
   * 마크다운에서 base64 이미지를 추출하여 VLM으로 OCR 처리 후 대체
   */
  private String processBase64Images(final String markdown) {
    // base64 인라인 이미지 패턴: ![Image](data:image/png;base64,...)
    final Pattern imagePattern = Pattern.compile("!\\[Image\\]\\(data:image/([^;]+);base64,([^)]+)\\)");
    final Matcher matcher = imagePattern.matcher(markdown);
    final StringBuilder result = new StringBuilder();

    while (matcher.find()) {
      final String base64Data = matcher.group(2);
      try {
        // base64 디코딩
        final byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Data);
        // VLM OCR 처리
        final String ocrText = vlmClient.ocr(imageBytes);
        matcher.appendReplacement(result, Matcher.quoteReplacement(ocrText));
      } catch (final Exception e) {
        log.warn("이미지 처리 실패, 원본 유지: {}", e.getMessage());
        matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
      }
    }
    matcher.appendTail(result);

    return result.toString();
  }

  // ============================================================
  // 시나리오 2: 이미지 → VLM 직접 처리
  // ============================================================

  @ParameterizedTest(name = "시나리오2: {0}")
  @MethodSource("documentFiles")
  @DisplayName("시나리오2: 문서 → 이미지 → VLM 직접")
  void scenario2(final String filename) throws IOException {
    assumeConverterAndVlmAvailable();

    final byte[] content = loadInput(filename);
    final String contentType = getContentType(filename);
    final String result = executeScenario2(content, filename, contentType);

    assertNotNull(result);
    assertFalse(result.isBlank());
    saveOutput("scenario2_" + getBaseName(filename) + ".md", result);
    System.out.println("시나리오2 " + filename + " 결과:\n" + truncate(result, 1000));
  }

  /**
   * 시나리오 2 실행:
   * 1. 문서 → 이미지 변환
   * 2. 각 이미지를 VLM으로 OCR
   * 3. 결과 취합
   */
  private String executeScenario2(final byte[] content, final String filename, final String contentType)
      throws IOException {
    // 1. 문서 → 이미지 변환
    final ImageConvertResult imagesResult = converterClient.convertToImages(
        content, filename, contentType, "png", testDpi);

    // 이미지 저장
    for (final ImagePage page : imagesResult.pages()) {
      saveOutputBytes("scenario2_" + getBaseName(filename) + "_page" + page.page() + ".png", page.content());
    }

    // 2. 각 이미지를 VLM으로 OCR
    final List<String> vlmResults = new ArrayList<>();
    for (final ImagePage page : imagesResult.pages()) {
      final String ocrText = vlmClient.ocr(page.content());
      vlmResults.add("## 페이지 " + page.page() + "\n\n" + ocrText);
    }

    // 3. 결과 취합
    final StringBuilder result = new StringBuilder();
    result.append("# ").append(filename).append(" VLM OCR 결과\n\n");
    result.append("총 ").append(imagesResult.totalPages()).append(" 페이지\n\n");
    for (final String vlmResult : vlmResults) {
      result.append(vlmResult).append("\n\n");
    }

    return result.toString();
  }

  // ============================================================
  // 헬퍼 메서드
  // ============================================================

  private void assumeAllServicesAvailable() {
    final boolean converterAvailable = converterClient.isAvailable();
    final boolean doclingAvailable = doclingClient.isAvailable();
    final boolean vlmAvailable = vlmClient.isAvailable();
    log.info("서비스 상태: Converter={}, Docling={}, VLM={}", converterAvailable, doclingAvailable, vlmAvailable);
    assumeTrue(converterAvailable, "Converter 서버 연결 불가 - 테스트 스킵");
    assumeTrue(doclingAvailable, "Docling 서버 연결 불가 - 테스트 스킵");
    assumeTrue(vlmAvailable, "VLM 서버 연결 불가 - 테스트 스킵");
  }

  private void assumeConverterAndVlmAvailable() {
    assumeTrue(converterClient.isAvailable(), "Converter 서버 연결 불가 - 테스트 스킵");
    assumeTrue(vlmClient.isAvailable(), "VLM 서버 연결 불가 - 테스트 스킵");
  }

  private byte[] loadInput(final String filename) throws IOException {
    final Path filePath = inputDir.resolve(filename);
    if (!Files.exists(filePath)) {
      throw new IOException("파일을 찾을 수 없음: " + filePath.toAbsolutePath());
    }
    return Files.readAllBytes(filePath);
  }

  private void saveOutput(final String filename, final String content) throws IOException {
    final Path outputPath = outputDir.resolve(filename);
    Files.writeString(outputPath, content);
    System.out.println("저장됨: " + outputPath.toAbsolutePath());
  }

  private void saveOutputBytes(final String filename, final byte[] content) throws IOException {
    final Path outputPath = outputDir.resolve(filename);
    Files.write(outputPath, content);
    System.out.println("저장됨: " + outputPath.toAbsolutePath());
  }

  private String getBaseName(final String filename) {
    final int dotIndex = filename.lastIndexOf('.');
    return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
  }

  private String truncate(final String text, final int maxLength) {
    if (text.length() <= maxLength) {
      return text;
    }
    return text.substring(0, maxLength) + "...";
  }
}

package me.hanju.parsekit.vlm;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * VlmClient 테스트 - 생성자 및 파라미터 검증
 */
class VlmClientTest {

  private static String vlmUrl;
  private static String vlmModel;
  private static VlmClient vlmClient;

  @BeforeAll
  static void setUp() throws IOException {
    vlmUrl = resolveProperty("parsekit.vlm.url");
    vlmModel = resolveProperty("parsekit.vlm.model");
    vlmClient = new VlmClient(WebClient.builder(), vlmUrl, vlmModel);
  }

  private static String resolveProperty(final String key) throws IOException {
    final Properties props = new Properties();
    try (InputStream is = VlmClientTest.class.getClassLoader().getResourceAsStream("application.properties")) {
      props.load(is);
    }
    final String value = props.getProperty(key);
    if (value != null && value.startsWith("${") && value.contains(":")) {
      final String envKey = value.substring(2, value.indexOf(':'));
      final String defaultValue = value.substring(value.indexOf(':') + 1, value.length() - 1);
      final String envValue = System.getenv(envKey);
      return envValue != null ? envValue : defaultValue;
    }
    return value;
  }

  // === 생성자 테스트 ===

  @Test
  @DisplayName("생성자 - webClientBuilder가 null이면 예외 발생")
  void constructor_nullWebClientBuilder_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> new VlmClient(null, vlmUrl, vlmModel));
  }

  @Test
  @DisplayName("생성자 - baseUrl이 null이면 예외 발생")
  void constructor_nullBaseUrl_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> new VlmClient(WebClient.builder(), null, vlmModel));
  }

  @Test
  @DisplayName("생성자 - baseUrl이 빈 문자열이면 예외 발생")
  void constructor_emptyBaseUrl_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> new VlmClient(WebClient.builder(), "", vlmModel));
  }

  @Test
  @DisplayName("생성자 - baseUrl이 공백만 있으면 예외 발생")
  void constructor_blankBaseUrl_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> new VlmClient(WebClient.builder(), "   ", vlmModel));
  }

  @Test
  @DisplayName("생성자 - model이 null이면 예외 발생")
  void constructor_nullModel_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> new VlmClient(WebClient.builder(), vlmUrl, null));
  }

  @Test
  @DisplayName("생성자 - model이 빈 문자열이면 예외 발생")
  void constructor_emptyModel_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> new VlmClient(WebClient.builder(), vlmUrl, ""));
  }

  @Test
  @DisplayName("생성자 - model이 공백만 있으면 예외 발생")
  void constructor_blankModel_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> new VlmClient(WebClient.builder(), vlmUrl, "   "));
  }

  // === OCR 파라미터 검증 테스트 ===

  @Test
  @DisplayName("OCR - imageBytes가 null이면 예외 발생")
  void ocr_nullImageBytes_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> vlmClient.ocr(null));
  }

  @Test
  @DisplayName("OCR - imageBytes가 빈 배열이면 예외 발생")
  void ocr_emptyImageBytes_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> vlmClient.ocr(new byte[0]));
  }

  // === parse 파라미터 검증 테스트 ===

  @Test
  @DisplayName("parse - content가 null이면 예외 발생")
  void parse_nullContent_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> vlmClient.parse(null, "test.png"));
  }

  @Test
  @DisplayName("parse - content가 빈 배열이면 예외 발생")
  void parse_emptyContent_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> vlmClient.parse(new byte[0], "test.png"));
  }

  @Test
  @DisplayName("parse - filename이 null이면 예외 발생")
  void parse_nullFilename_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> vlmClient.parse(new byte[] { 1, 2, 3 }, null));
  }

  @Test
  @DisplayName("parse - filename이 빈 문자열이면 예외 발생")
  void parse_emptyFilename_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> vlmClient.parse(new byte[] { 1, 2, 3 }, ""));
  }

  @Test
  @DisplayName("parse - filename이 공백만 있으면 예외 발생")
  void parse_blankFilename_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> vlmClient.parse(new byte[] { 1, 2, 3 }, "   "));
  }

  // === 서비스 가용성 테스트 ===

  @Test
  @DisplayName("서비스 가용성 확인")
  void isAvailable() {
    final boolean available = vlmClient.isAvailable();
    System.out.println("VLM 서비스 가용 여부: " + available);
  }
}

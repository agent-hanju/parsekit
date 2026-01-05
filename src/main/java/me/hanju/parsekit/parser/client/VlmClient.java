package me.hanju.parsekit.parser.client;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;
import me.hanju.parsekit.common.FileTypeDetector;
import me.hanju.parsekit.parser.config.ParserProperties;
import me.hanju.parsekit.parser.config.ParserProperties.VlmServer;
import me.hanju.parsekit.parser.dto.VlmChatRequest;
import me.hanju.parsekit.parser.dto.VlmChatRequest.ImageContent;
import me.hanju.parsekit.parser.dto.VlmChatRequest.TextContent;
import me.hanju.parsekit.parser.dto.VlmChatResponse;
import me.hanju.parsekit.parser.exception.VlmClientException;

@Slf4j
@Component
@RefreshScope
@ConditionalOnProperty(prefix = "parser.vlm", name = "servers[0].base-url")
public class VlmClient {

  private final List<VlmEndpoint> endpoints;
  private final AtomicInteger counter = new AtomicInteger(0);
  private final Duration timeout;
  private final int maxTokens;
  private final double temperature;
  private final String defaultPrompt;

  public VlmClient(ParserProperties properties) {
    ParserProperties.VlmProperties vlm = properties.getVlm();

    int bufferSize = vlm.getMaxBufferSize() > 0 ? vlm.getMaxBufferSize() : 16 * 1024 * 1024;
    ExchangeStrategies strategies = ExchangeStrategies.builder()
        .codecs(config -> config.defaultCodecs().maxInMemorySize(bufferSize))
        .build();

    this.endpoints = vlm.getServers().stream()
        .filter(server -> server.getBaseUrl() != null && !server.getBaseUrl().isBlank())
        .map(server -> new VlmEndpoint(
            WebClient.builder()
                .baseUrl(server.getBaseUrl())
                .exchangeStrategies(strategies)
                .build(),
            server.getModel()))
        .toList();

    this.timeout = vlm.getTimeout() != null ? vlm.getTimeout() : Duration.ofMinutes(2);
    this.maxTokens = vlm.getMaxTokens() > 0 ? vlm.getMaxTokens() : 4096;
    this.temperature = vlm.getTemperature() > 0 ? vlm.getTemperature() : 0.01;
    this.defaultPrompt = vlm.getDefaultPrompt() != null ? vlm.getDefaultPrompt()
        : "Extract all text from this image accurately. Return only the extracted text without any additional explanation.";

    log.info("VlmClient initialized with {} servers", endpoints.size());
    for (VlmServer server : vlm.getServers()) {
      log.info("  - {} (model: {})", server.getBaseUrl(), server.getModel());
    }
  }

  private VlmEndpoint getNextEndpoint() {
    int index = Math.abs(counter.getAndIncrement() % endpoints.size());
    return endpoints.get(index);
  }

  public String ocr(final String base64EncodedUri) {
    return ocr(base64EncodedUri, defaultPrompt);
  }

  public String ocr(final String base64EncodedUri, final String prompt) {
    if (!FileTypeDetector.validateBase64EncodedUri(base64EncodedUri)) {
      throw new IllegalArgumentException("not valid Base64EncodedUri");
    }

    VlmEndpoint endpoint = getNextEndpoint();

    VlmChatRequest request = new VlmChatRequest(
        endpoint.model(),
        List.of(new VlmChatRequest.Message(
            "user",
            List.of(
                new ImageContent(base64EncodedUri),
                new TextContent(prompt)))),
        maxTokens,
        temperature);

    try {
      VlmChatResponse response = endpoint.client().post()
          .uri("/v1/chat/completions")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(request)
          .retrieve()
          .bodyToMono(VlmChatResponse.class)
          .block(timeout);

      if (response == null) {
        throw new VlmClientException("Empty response from VLM service",
            new IllegalStateException("Response is null"));
      }

      String content = response.getContent();
      if (content == null) {
        throw new VlmClientException("Invalid response format: missing content",
            new IllegalStateException("Content is null"));
      }

      return content;

    } catch (VlmClientException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to OCR image", e);
      throw new VlmClientException("Failed to OCR image: " + e.getMessage(), e);
    }
  }

  private record VlmEndpoint(WebClient client, String model) {
  }
}

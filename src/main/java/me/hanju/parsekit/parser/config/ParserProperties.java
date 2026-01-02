package me.hanju.parsekit.parser.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "parser")
public class ParserProperties {

  private DoclingProperties docling = new DoclingProperties();
  private VlmProperties vlm = new VlmProperties();

  @Getter
  @Setter
  public static class DoclingProperties {
    private List<String> baseUrls = new ArrayList<>();
    private Duration timeout;
    private int maxBufferSize;
  }

  @Getter
  @Setter
  public static class VlmProperties {
    private List<VlmServer> servers = new ArrayList<>();
    private Duration timeout;
    private int maxBufferSize;
    private int maxTokens;
    private double temperature = 0.01;
    private String defaultPrompt = "Extract all text from this image accurately. Return only the extracted text.";
    private String embeddedImagePrompt = "This is an embedded image from a document. Extract and describe all text, diagrams, charts, or visual content. Format the output as markdown.";
    private String imageFormat = "png";
  }

  @Getter
  @Setter
  public static class VlmServer {
    private String baseUrl;
    private String model;
  }
}

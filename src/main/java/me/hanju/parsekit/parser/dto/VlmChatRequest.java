package me.hanju.parsekit.parser.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VlmChatRequest(
    @JsonProperty("model") String model,
    @JsonProperty("messages") List<Message> messages,
    @JsonProperty("max_tokens") int maxTokens,
    @JsonProperty("temperature") double temperature) {

  public record Message(
      @JsonProperty("role") String role,
      @JsonProperty("content") List<Content> content) {
  }

  public sealed interface Content permits TextContent, ImageContent {
  }

  public record TextContent(
      @JsonProperty("type") String type,
      @JsonProperty("text") String text) implements Content {

    public TextContent(String text) {
      this("text", text);
    }
  }

  public record ImageContent(
      @JsonProperty("type") String type,
      @JsonProperty("image_url") ImageUrl imageUrl) implements Content {

    public ImageContent(String url) {
      this("image_url", new ImageUrl(url));
    }
  }

  public record ImageUrl(
      @JsonProperty("url") String url) {
  }
}

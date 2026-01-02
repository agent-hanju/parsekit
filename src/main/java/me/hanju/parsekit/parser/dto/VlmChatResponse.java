package me.hanju.parsekit.parser.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VlmChatResponse(
    @JsonProperty("id") String id,
    @JsonProperty("object") String object,
    @JsonProperty("created") Long created,
    @JsonProperty("model") String model,
    @JsonProperty("choices") List<Choice> choices,
    @JsonProperty("usage") Usage usage) {

  public record Choice(
      @JsonProperty("index") int index,
      @JsonProperty("message") Message message,
      @JsonProperty("finish_reason") String finishReason) {
  }

  public record Message(
      @JsonProperty("role") String role,
      @JsonProperty("content") String content) {
  }

  public record Usage(
      @JsonProperty("prompt_tokens") int promptTokens,
      @JsonProperty("completion_tokens") int completionTokens,
      @JsonProperty("total_tokens") int totalTokens) {
  }

  public String getContent() {
    if (choices == null || choices.isEmpty()) {
      return null;
    }
    Message message = choices.get(0).message();
    return message != null ? message.content() : null;
  }
}

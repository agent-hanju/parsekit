package me.hanju.parsekit.converter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PageImageResponse(
    @JsonProperty("page") int page,
    @JsonProperty("content") String content, // Base64 encoded
    @JsonProperty("size") int size,
    @JsonProperty("total_pages") int totalPages) {
}

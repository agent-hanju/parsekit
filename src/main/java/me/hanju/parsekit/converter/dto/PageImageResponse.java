package me.hanju.parsekit.converter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PageImageResponse(
    @JsonProperty("page") int page,
    @JsonProperty("encoded_uri") String encodedUri,
    @JsonProperty("size") int size,
    @JsonProperty("total_pages") int totalPages) {
}

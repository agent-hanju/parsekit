package me.hanju.parsekit.parser.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DoclingConvertRequest(
    @JsonProperty("image_export_mode") String imageExportMode) {
}

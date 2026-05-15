package com.bct.ngtpa.apiservice.adapter.out.configserver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HTTP response DTO for a single Config Service entry.
 *
 * <p>Field names are assumed to be camelCase based on the Config Service query
 * parameter naming convention. Verify the exact JSON keys against the actual
 * Config Service before promoting to production.
 *
 * <p>Must not be used outside {@code adapter/out/configserver} — enforced by ArchUnit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigServiceResponse {

    @JsonProperty("application")
    private String application;

    @JsonProperty("profile")
    private String profile;

    @JsonProperty("label")
    private String label;

    @JsonProperty("configKey")
    private String configKey;

    @JsonProperty("configValue")
    private String configValue;
}

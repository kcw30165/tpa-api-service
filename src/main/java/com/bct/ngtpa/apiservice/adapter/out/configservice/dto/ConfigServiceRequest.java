package com.bct.ngtpa.apiservice.adapter.out.configservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HTTP request DTO for the Config Service PUT /api/configs endpoint.
 *
 * <p>Field names are assumed to be camelCase based on the Config Service query
 * parameter naming convention. Verify the exact JSON keys against the actual
 * Config Service {@code ConfigRequest} before promoting to production.
 *
 * <p>Must not be used outside {@code adapter/out/configservice} — enforced by ArchUnit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigServiceRequest {

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

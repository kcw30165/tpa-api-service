package com.bct.ngtpa.apiservice.adapter.out.apim.metadata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeneralMetadataApimRow(
    @JsonProperty("policy-no") String policyNo,
    @JsonProperty("metadata-name") String metadataName,
    @JsonProperty("metadata-type") String metadataType,
    @JsonProperty("metadata-value") String metadataValue) {}

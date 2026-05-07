package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApimErrorResponse(
        String id,
        String error,
        @JsonAlias({"error_description", "error_desciption"})
        String errorDescription
) {}

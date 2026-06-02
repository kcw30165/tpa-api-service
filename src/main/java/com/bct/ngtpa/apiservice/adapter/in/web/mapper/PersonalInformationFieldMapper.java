package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;

import java.util.Map;

/**
 * Contract for mapping APIM personal-information payloads and config into BFF page field models.
 *
 * Note: This is only a minimal interface (no implementation). Tests will be added first (TDD) and
 * an implementation will be provided in a later step.
 */
public interface PersonalInformationFieldMapper {

    /**
     * Map raw APIM data and APIM config into a map of BFF field id -> field model (represented as Object here).
     */
    Map<String, Object> map(Map<String, Object> apimData,
                            Map<String, String> apimConfig,
                            BffPagesProperties bffPagesProperties);
}

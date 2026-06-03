package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;

import java.util.Map;

/**
 * Contract for mapping APIM personal-information payloads and config into the BFF page/form schema.
 */
public interface PersonalInformationFieldMapper {

    /**
     * Map raw APIM data and APIM config into the Personal Information page/form response contract.
     */
    Map<String, Object> map(Map<String, Object> apimData,
                            Map<String, String> apimConfig,
                            BffPagesProperties bffPagesProperties,
                            String language);
}

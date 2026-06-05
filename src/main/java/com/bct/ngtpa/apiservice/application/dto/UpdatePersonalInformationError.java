package com.bct.ngtpa.apiservice.application.dto;

import java.util.List;

/**
 * Raw update validation/business error returned by APIM/TAS, normalized at the web adapter boundary.
 */
public record UpdatePersonalInformationError(
        String type,
        List<String> fields,
        String code) {

    public UpdatePersonalInformationError {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}

package com.bct.ngtpa.apiservice.application.dto;

import java.util.List;

public record ReferenceDataCountriesResult(
        List<ReferenceDataOption> countries,
        List<ReferenceDataOption> callingCodes) {
}

package com.bct.ngtpa.apiservice.application.dto;

import java.util.List;

public record UpdatePersonalInformationAccountResult(
        boolean success,
        boolean selected,
        String policyNo,
        String certNo,
        String env,
        String refNo,
        String submitDate,
        String submitTime,
        List<UpdatePersonalInformationError> errors) {

    public UpdatePersonalInformationAccountResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}

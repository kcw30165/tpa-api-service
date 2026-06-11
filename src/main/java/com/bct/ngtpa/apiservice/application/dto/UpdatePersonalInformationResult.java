package com.bct.ngtpa.apiservice.application.dto;

import java.util.List;

public record UpdatePersonalInformationResult(
        boolean success,
        String refNo,
        String submitDate,
        String submitTime,
        List<UpdatePersonalInformationError> errors,
        List<UpdatePersonalInformationAccountResult> accountResults) {

    public UpdatePersonalInformationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
        accountResults = accountResults == null ? List.of() : List.copyOf(accountResults);
    }

    public UpdatePersonalInformationResult(boolean success, String refNo, String submitDate, String submitTime) {
        this(success, refNo, submitDate, submitTime, List.of(), List.of());
    }

    public UpdatePersonalInformationResult(boolean success, String refNo, String submitDate, String submitTime,
            List<UpdatePersonalInformationError> errors) {
        this(success, refNo, submitDate, submitTime, errors, List.of());
    }
}

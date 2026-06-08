package com.bct.ngtpa.apiservice.application.dto;

import java.util.List;

public record UpdatePersonalInformationResult(
        boolean success,
        String refNo,
        String submitDate,
        String submitTime,
        List<UpdatePersonalInformationError> errors) {

    public UpdatePersonalInformationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public UpdatePersonalInformationResult(boolean success, String refNo, String submitDate, String submitTime) {
        this(success, refNo, submitDate, submitTime, List.of());
    }
}

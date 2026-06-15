package com.bct.ngtpa.apiservice.application.dto;

import java.util.List;

public record UpdatePersonalInformationResult(
        boolean selected,
        boolean success,
        String policyNo,
        String certNo,
        String env,
        String refNo,
        String submitDate,
        String submitTime,
        List<UpdatePersonalInformationError> errors) {

    public UpdatePersonalInformationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public UpdatePersonalInformationResult(boolean success, String refNo, String submitDate, String submitTime) {
        this(true, success, null, null, null, refNo, submitDate, submitTime, List.of());
    }

    public UpdatePersonalInformationResult(
            boolean success,
            String refNo,
            String submitDate,
            String submitTime,
            List<UpdatePersonalInformationError> errors,
            List<UpdatePersonalInformationResult> ignoredAccountResults) {
        this(true, success, null, null, null, refNo, submitDate, submitTime, errors);
    }
}

package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.List;

public record PersonalInformationUpdateResultResponse(
        boolean selected,
        boolean success,
        String policyNo,
        String certNo,
        String env,
        String refNo,
        String submitDate,
        String submitTime,
        List<ApiError> errors) {

    public PersonalInformationUpdateResultResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public PersonalInformationUpdateResultResponse(String refNo, String submitDate, String submitTime) {
        this(true, true, null, null, null, refNo, submitDate, submitTime, List.of());
    }
}

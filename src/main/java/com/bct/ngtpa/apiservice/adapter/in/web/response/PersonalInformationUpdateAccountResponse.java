package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.List;

public record PersonalInformationUpdateAccountResponse(
        boolean selected,
        boolean success,
        String policyNo,
        String certNo,
        String env,
        String refNo,
        String submitDate,
        String submitTime,
        List<ApiError> errors) {

    public PersonalInformationUpdateAccountResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}

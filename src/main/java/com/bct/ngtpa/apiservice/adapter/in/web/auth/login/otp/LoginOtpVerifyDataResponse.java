package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.otp;

import java.util.List;

public record LoginOtpVerifyDataResponse(Integer remainingVerifyAttempts, List<LoginOtpActionResponse> actions) {
    public LoginOtpVerifyDataResponse {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}

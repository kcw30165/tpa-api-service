package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ForgotPasswordPasswordSetupDataResponse(Integer remainingVerifyAttempts) {}

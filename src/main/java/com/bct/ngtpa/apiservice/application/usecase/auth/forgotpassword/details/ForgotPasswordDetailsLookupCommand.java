package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details;
public record ForgotPasswordDetailsLookupCommand(String searchBy, String searchValue, String dateOfBirth,
        String email, String mobile, String customerId, String requestId) {}

package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationIdentityType;

public record ForgotPasswordUsernameLookupResult(
        ForgotPasswordUsernameLookupStatus status,
        String customerId,
        String userId,
        RegistrationIdentityType identityType) {

    public static ForgotPasswordUsernameLookupResult confirmed(
            String customerId,
            String userId,
            RegistrationIdentityType identityType) {
        return new ForgotPasswordUsernameLookupResult(
                ForgotPasswordUsernameLookupStatus.CONFIRMED,
                customerId,
                userId,
                identityType);
    }

    public static ForgotPasswordUsernameLookupResult notFound() {
        return new ForgotPasswordUsernameLookupResult(
                ForgotPasswordUsernameLookupStatus.NOT_FOUND,
                null,
                null,
                null);
    }

    public static ForgotPasswordUsernameLookupResult systemFailed() {
        return new ForgotPasswordUsernameLookupResult(
                ForgotPasswordUsernameLookupStatus.SYSTEM_FAILED,
                null,
                null,
                null);
    }

    @Override
    public String toString() {
        return "ForgotPasswordUsernameLookupResult[status=" + status
                + ", customerId=<redacted>, userId=<redacted>, identityType=" + identityType + "]";
    }
}

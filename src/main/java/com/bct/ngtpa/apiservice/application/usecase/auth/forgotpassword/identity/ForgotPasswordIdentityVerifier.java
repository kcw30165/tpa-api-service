package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationIdentityType;

public final class ForgotPasswordIdentityVerifier {

    public RegistrationIdentityType mapPublicIdentityType(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "GPN" -> RegistrationIdentityType.GPN;
            case "GPFS" -> RegistrationIdentityType.GPFS;
            case "STAFF_NO" -> RegistrationIdentityType.STAFFNO;
            case "ID" -> RegistrationIdentityType.ID;
            default -> null;
        };
    }

    public boolean matches(
            String requestedType,
            String requestedValue,
            String protectedType,
            String protectedValue) {
        RegistrationIdentityType requested = mapPublicIdentityType(requestedType);
        RegistrationIdentityType stored = mapProtectedIdentityType(protectedType);
        return requested != null
                && requested == stored
                && requestedValue != null
                && protectedValue != null
                && requestedValue.trim().equals(protectedValue.trim());
    }

    private RegistrationIdentityType mapProtectedIdentityType(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "GPN" -> RegistrationIdentityType.GPN;
            case "GPFS" -> RegistrationIdentityType.GPFS;
            case "STAFF_NO", "STAFFNO" -> RegistrationIdentityType.STAFFNO;
            case "ID" -> RegistrationIdentityType.ID;
            default -> null;
        };
    }
}

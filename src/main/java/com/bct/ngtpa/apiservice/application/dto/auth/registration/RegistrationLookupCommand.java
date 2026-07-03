package com.bct.ngtpa.apiservice.application.dto.auth.registration;

import java.util.Objects;

public record RegistrationLookupCommand(
        RegistrationIdentityType identityType,
        String identityValue,
        String dateOfBirth,
        RegistrationContactType contactType,
        String contactValue) {
    public RegistrationLookupCommand {
        Objects.requireNonNull(identityType, "identityType must not be null");
        Objects.requireNonNull(identityValue, "identityValue must not be null");
        Objects.requireNonNull(dateOfBirth, "dateOfBirth must not be null");
        Objects.requireNonNull(contactType, "contactType must not be null");
        Objects.requireNonNull(contactValue, "contactValue must not be null");
        identityValue = identityValue.trim();
        dateOfBirth = dateOfBirth.trim();
        contactValue = contactValue.trim();
        if (identityValue.isBlank() || dateOfBirth.isBlank() || contactValue.isBlank()) {
            throw new IllegalArgumentException("registration lookup command fields must not be blank");
        }
    }

    @Override
    public String toString() {
        return "RegistrationLookupCommand[identityType=" + identityType
                + ", identityValue=<redacted>, dateOfBirth=<redacted>, contactType=" + contactType
                + ", contactValue=<redacted>]";
    }
}

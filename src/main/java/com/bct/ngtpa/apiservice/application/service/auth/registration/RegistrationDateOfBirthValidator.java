package com.bct.ngtpa.apiservice.application.service.auth.registration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

final class RegistrationDateOfBirthValidator {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private RegistrationDateOfBirthValidator() {
    }

    static boolean isValid(String value) {
        if (value == null || value.trim().isBlank()) {
            return false;
        }
        try {
            LocalDate.parse(value.trim(), FORMATTER);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}

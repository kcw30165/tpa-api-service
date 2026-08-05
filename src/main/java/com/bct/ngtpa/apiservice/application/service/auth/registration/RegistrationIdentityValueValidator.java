package com.bct.ngtpa.apiservice.application.service.auth.registration;

import java.util.regex.Pattern;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;

final class RegistrationIdentityValueValidator {
    private static final Pattern GPN = Pattern.compile("^[0-9]{8}$");
    private static final Pattern GPFS = Pattern.compile("^[0-9]{10}$");
    private static final Pattern STAFFNO = Pattern.compile("^[A-Za-z0-9]+$");
    private static final Pattern NO_SPACE_MAX_40 = Pattern.compile("^\\S{1,40}$");

    private RegistrationIdentityValueValidator() {
    }

    static boolean isValid(MemberIdentityType type, String value) {
        if (type == null || value == null || value.trim().isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        return switch (type) {
            case GPN -> GPN.matcher(trimmed).matches();
            case GPFS -> GPFS.matcher(trimmed).matches();
            case STAFFNO -> STAFFNO.matcher(trimmed).matches();
            case ID -> NO_SPACE_MAX_40.matcher(trimmed).matches();
        };
    }
}

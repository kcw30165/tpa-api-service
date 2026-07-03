package com.bct.ngtpa.apiservice.application.service.auth.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationIdentityType;
import java.util.regex.Pattern;

final class RegistrationIdentityValueValidator {
    private static final Pattern GPN = Pattern.compile("^[0-9]{8}$");
    private static final Pattern GPFS = Pattern.compile("^[0-9]{10}$");
    private static final Pattern STAFF_NO = Pattern.compile("^[A-Za-z0-9]+$");
    private static final Pattern HKID = Pattern.compile("^[A-Za-z]{1,2}[0-9]{6}(?:[0-9A])?$|^[A-Za-z]{1,2}[0-9]{6}\\([0-9A]\\)$");
    private static final Pattern NO_SPACE_MAX_40 = Pattern.compile("^\\S{1,40}$");

    private RegistrationIdentityValueValidator() {
    }

    static boolean isValid(RegistrationIdentityType type, String value) {
        if (type == null || value == null || value.trim().isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        return switch (type) {
            case GPN -> GPN.matcher(trimmed).matches();
            case GPFS -> GPFS.matcher(trimmed).matches();
            case STAFF_NO -> STAFF_NO.matcher(trimmed).matches();
            case HKID -> HKID.matcher(trimmed).matches();
            case PASSPORT, OTHER_NATIONAL_ID -> NO_SPACE_MAX_40.matcher(trimmed).matches();
        };
    }
}

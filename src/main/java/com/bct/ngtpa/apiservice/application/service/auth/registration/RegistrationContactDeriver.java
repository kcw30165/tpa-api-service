package com.bct.ngtpa.apiservice.application.service.auth.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationStartContact;
import java.util.Locale;

public class RegistrationContactDeriver {
    public RegistrationStartContact derive(String email, String mobile) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedMobile = normalizeMobile(mobile);
        boolean hasEmail = !normalizedEmail.isBlank();
        boolean hasMobile = !normalizedMobile.isBlank();
        if (hasEmail == hasMobile) {
            throw new IllegalArgumentException("exactly one contact is required");
        }
        if (hasEmail) {
            return new RegistrationStartContact(
                    RegistrationContactType.EMAIL,
                    normalizedEmail,
                    RegistrationContactMasker.maskEmail(normalizedEmail));
        }
        return new RegistrationStartContact(
                RegistrationContactType.MOBILE,
                normalizedMobile,
                RegistrationContactMasker.maskMobile(normalizedMobile));
    }

    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizeMobile(String mobile) {
        return mobile == null ? "" : mobile.trim();
    }
}

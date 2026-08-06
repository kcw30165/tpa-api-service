package com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp;

import java.util.*;

public final class ForgotPasswordOtpTemplates {
    private final Map<String, String> sms;
    private final Map<String, String> subjects;
    private final Map<String, String> bodies;

    public ForgotPasswordOtpTemplates(Map<String, String> sms, Map<String, String> subjects,
            Map<String, String> bodies) {
        this.sms = Map.copyOf(sms);
        this.subjects = Map.copyOf(subjects);
        this.bodies = Map.copyOf(bodies);
        for (String locale : List.of("en", "zh_HK")) {
            require(this.sms.get(locale), "sms." + locale, true);
            require(this.subjects.get(locale), "subject." + locale, false);
            require(this.bodies.get(locale), "body." + locale, true);
        }
    }

    public String sms(String language) {
        return sms.get(normalize(language));
    }

    public String emailSubject(String language) {
        return subjects.get(normalize(language));
    }

    public String emailBody(String language) {
        return bodies.get(normalize(language));
    }

    private String normalize(String value) {
        if (value == null)
            return "en";
        String v = value.trim().replace('-', '_');
        return v.equalsIgnoreCase("zh_HK") ? "zh_HK" : "en";
    }

    private void require(String value, String name, boolean placeholders) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(name + " required");
        if (placeholders)
            for (String p : List.of("{code}", "{ttlValue}", "{ttlUnit}"))
                if (!value.contains(p))
                    throw new IllegalArgumentException(name + " missing " + p);
    }

    public static ForgotPasswordOtpTemplates approved() {
        return new ForgotPasswordOtpTemplates(
                Map.of("en",
                        "Your BCT forgot password verification code is {code}. It is valid for {ttlValue} {ttlUnit}.",
                        "zh_HK", "您的 BCT 忘記密碼驗證碼為 {code}，有效期限為 {ttlValue} {ttlUnit}。"),
                Map.of("en", "BCT forgot password verification code", "zh_HK", "BCT 忘記密碼驗證碼"),
                Map.of("en",
                        "Your BCT forgot password verification code is {code}. It is valid for {ttlValue} {ttlUnit}.",
                        "zh_HK", "您的 BCT 忘記密碼驗證碼為 {code}，有效期限為 {ttlValue} {ttlUnit}。"));
    }

    @Override
    public String toString() {
        return "ForgotPasswordOtpTemplates[localizedTemplates=<redacted>]";
    }
}

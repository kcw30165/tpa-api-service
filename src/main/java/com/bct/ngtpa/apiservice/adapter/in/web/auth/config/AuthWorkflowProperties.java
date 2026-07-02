package com.bct.ngtpa.apiservice.adapter.in.web.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "auth-workflows")
@Getter
@Setter
public class AuthWorkflowProperties {

    private Registration registration;

    @Getter
    @Setter
    public static class Registration {
        private final PreStartChallenge preStartChallenge = new PreStartChallenge();
        private final Captcha captcha = new Captcha();
        private final Otp otp = new Otp();
        private final Username username = new Username();
        private final Password password = new Password();
        private final RateLimit rateLimit = new RateLimit();
        private Map<String, String> identityTypeMapping = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    public static class PreStartChallenge {
        private boolean enabled;
        private PreStartChallengeType type;
    }

    public enum PreStartChallengeType {
        CAPTCHA
    }

    @Getter
    @Setter
    public static class Captcha {
        private int length;
        private String alphabet;
        private boolean caseSensitive;
        private int ttlSeconds;
        private int maxAttempts;
    }

    @Getter
    @Setter
    public static class Otp {
        private int length;
        private boolean numericOnly;
        private int ttlSeconds;
        private int resendCooldownSeconds;
        private int maxVerifyAttempts;
        private int maxResendAttempts;
        private boolean invalidatePreviousOnResend;
        private boolean resetVerifyAttemptsOnResend;
        private String smsTemplate;
        private final OtpEmail emailTemplate = new OtpEmail();
    }

    @Getter
    @Setter
    public static class OtpEmail {
        private String subject;
        private String body;
    }

    @Getter
    @Setter
    public static class Username {
        private int minLength;
        private int maxLength;
        private boolean noSpaces;
        private boolean caseInsensitive;
        private boolean requiredDigit;
        private boolean requiredSpecial;
        private String allowedSpecialChars;
    }

    @Getter
    @Setter
    public static class Password {
        private int minLength;
        private int maxLength;
        private boolean noSpaces;
        private boolean notSameAsUsernameIgnoreCase;
        private int requiredCategories;
        private String specialChars;
    }

    @Getter
    @Setter
    public static class RateLimit {
        private final RegistrationStartRateLimit registrationStart = new RegistrationStartRateLimit();
        private final OtpSendRateLimit otpSend = new OtpSendRateLimit();
        private final OtpVerifyRateLimit otpVerify = new OtpVerifyRateLimit();
    }

    @Getter
    @Setter
    public static class RegistrationStartRateLimit {
        private final RateLimitRule byIp = new RateLimitRule();
    }

    @Getter
    @Setter
    public static class OtpSendRateLimit {
        private final RateLimitRule byDestinationHash = new RateLimitRule();
    }

    @Getter
    @Setter
    public static class OtpVerifyRateLimit {
        private final RateLimitRule byRegistrationSession = new RateLimitRule();
    }

    @Getter
    @Setter
    public static class RateLimitRule {
        private boolean enabled;
        private int maxAttempts;
        private int windowSeconds;
    }
}
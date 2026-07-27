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
    private Login login;

    @Getter
    @Setter
    public static class Registration {
        private final PreStartChallenge preStartChallenge = new PreStartChallenge();
        private final Captcha captcha = new Captcha();
        private final Otp otp = new Otp();
        private final Message message = new Message();
        private final Username username = new Username();
        private final Password password = new Password();
        private Map<String, String> identityTypeMapping = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    public static class Login {
        private final LoginCredential credential = new LoginCredential();
        private final LoginOtp otp = new LoginOtp();
        private final LoginIdempotency idempotency = new LoginIdempotency();
        private final LoginSession session = new LoginSession();
        private final LoginAccount account = new LoginAccount();
        private final Message message = new Message();
    }

    @Getter
    @Setter
    public static class LoginCredential {
        private int usernameMinLength;
        private int usernameMaxLength;
        private int passwordMinLength;
        private int passwordMaxLength;
        private boolean usernameCaseInsensitive = true;
        private boolean usernameTrim = true;
        private String usernameNormalization = "ASCII_LOWERCASE";
        private String usernameAllowedPattern;
        private boolean passwordTrim;
        private boolean passwordCaseSensitive = true;
    }

    @Getter
    @Setter
    public static class LoginOtp {
        private int length;
        private boolean numericOnly = true;
        private int ttlSeconds;
        private int resendCooldownSeconds;
        private int maxVerifyAttempts;
        private boolean resendLimitEnabled;
        private int maxResendAttempts;
        private String smsTemplate;
        private final LoginOtpEmail emailTemplate = new LoginOtpEmail();
    }

    @Getter
    @Setter
    public static class LoginOtpEmail {
        private String subject;
        private String body;
    }

    @Getter
    @Setter
    public static class LoginIdempotency {
        private int startTtlSeconds;
        private int otpSendTtlSeconds;
        private int otpResendTtlSeconds;
        private int otpVerifyTtlSeconds;
        private int cancelTtlSeconds;
    }

    @Getter
    @Setter
    public static class LoginSession {
        private int ttlSeconds;
        private int idleTimeoutSeconds;
        private final AuthenticatedCookie cookie = new AuthenticatedCookie();
        private final AuthenticatedCsrf csrf = new AuthenticatedCsrf();
    }

    @Getter
    @Setter
    public static class AuthenticatedCookie {
        private String name = "portal_sid";
        private String path = "/api";
        private int maxAgeSeconds = 900;
        private boolean httpOnly = true;
        private boolean secure = true;
        private String sameSite = "None";
    }

    @Getter
    @Setter
    public static class AuthenticatedCsrf {
        private String cookieName = "XSRF-TOKEN";
        private String headerName = "X-XSRF-TOKEN";
        private String cookiePath = "/api";
        private int maxAgeSeconds = 900;
        private boolean secure = true;
        private String sameSite = "None";
    }

    @Getter
    @Setter
    public static class LoginAccount {
        private int identifierWidth;
        private char identifierPadCharacter = '0';
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
        private boolean resendLimitEnabled = true;
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
    public static class Message {
        private final DestinationOverride destinationOverride = new DestinationOverride();
    }

    @Getter
    @Setter
    public static class DestinationOverride {
        private String mobile;
        private String email;

        public void validateRequiredDestinations() {
            boolean mobileMissing = mobile == null || mobile.isBlank();
            boolean emailMissing = email == null || email.isBlank();
            if (!mobileMissing && !emailMissing) {
                return;
            }

            String missingProperties;
            if (mobileMissing && emailMissing) {
                missingProperties = "mobile and email";
            } else {
                missingProperties = mobileMissing ? "mobile" : "email";
            }
            throw new IllegalStateException(
                    "auth-workflows.registration.message.destination-override "
                            + "requires non-blank " + missingProperties + " configuration");
        }

        public String requiredMobile() {
            return requiredDestination(mobile, "mobile");
        }

        public String requiredEmail() {
            return requiredDestination(email, "email");
        }

        private String requiredDestination(String destination, String propertyName) {
            if (destination == null || destination.isBlank()) {
                throw new IllegalStateException(
                        "auth-workflows.registration.message.destination-override."
                                + propertyName + " must not be blank");
            }
            return destination.trim();
        }
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

    public void validateLoginPolicy() {
        if (login == null) {
            throw new IllegalStateException("auth-workflows.login configuration is required");
        }
        requirePositive(login.credential.usernameMinLength, "credential.username-min-length");
        requireRange(login.credential.usernameMinLength, login.credential.usernameMaxLength,
                "credential username length");
        if (login.credential.usernameAllowedPattern == null || login.credential.usernameAllowedPattern.isBlank()) {
            throw new IllegalStateException(
                    "auth-workflows.login.credential.username-allowed-pattern must not be blank");
        }
        if (!login.credential.passwordCaseSensitive || login.credential.passwordTrim) {
            throw new IllegalStateException("auth-workflows.login password must remain exact and case-sensitive");
        }
        requirePositive(login.credential.passwordMinLength, "credential.password-min-length");
        requireRange(login.credential.passwordMinLength, login.credential.passwordMaxLength,
                "credential password length");
        requirePositive(login.otp.length, "otp.length");
        requirePositive(login.otp.ttlSeconds, "otp.ttl-seconds");
        requireNonNegative(login.otp.resendCooldownSeconds, "otp.resend-cooldown-seconds");
        requirePositive(login.otp.maxVerifyAttempts, "otp.max-verify-attempts");
        if (login.otp.smsTemplate == null || !login.otp.smsTemplate.contains("{code}"))
            throw new IllegalStateException("auth-workflows.login.otp.sms-template must contain {code}");
        if (login.otp.emailTemplate.subject == null || login.otp.emailTemplate.subject.isBlank()
                || login.otp.emailTemplate.body == null || !login.otp.emailTemplate.body.contains("{code}"))
            throw new IllegalStateException("auth-workflows.login.otp.email-template is invalid");
        if (login.otp.resendLimitEnabled) {
            requirePositive(login.otp.maxResendAttempts, "otp.max-resend-attempts");
        }
        requirePositive(login.idempotency.startTtlSeconds, "idempotency.start-ttl-seconds");
        requirePositive(login.idempotency.otpSendTtlSeconds, "idempotency.otp-send-ttl-seconds");
        requirePositive(login.idempotency.otpResendTtlSeconds, "idempotency.otp-resend-ttl-seconds");
        requirePositive(login.idempotency.otpVerifyTtlSeconds, "idempotency.otp-verify-ttl-seconds");
        requirePositive(login.session.ttlSeconds, "session.ttl-seconds");
        requirePositive(login.session.idleTimeoutSeconds, "session.idle-timeout-seconds");
        requirePositive(login.account.identifierWidth, "account.identifier-width");
    }

    private void requirePositive(int value, String property) {
        if (value <= 0) {
            throw new IllegalStateException("auth-workflows.login." + property + " must be greater than zero");
        }
    }

    private void requireNonNegative(int value, String property) {
        if (value < 0) {
            throw new IllegalStateException("auth-workflows.login." + property + " must not be negative");
        }
    }

    private void requireRange(int minimum, int maximum, String property) {
        if (maximum < minimum) {
            throw new IllegalStateException(
                    "auth-workflows.login " + property + " maximum must not be less than minimum");
        }
    }

}
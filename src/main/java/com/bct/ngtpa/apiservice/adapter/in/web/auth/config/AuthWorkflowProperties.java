package com.bct.ngtpa.apiservice.adapter.in.web.auth.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth-workflows")
@Getter
@Setter
public class AuthWorkflowProperties {

  private Registration registration;
  private Login login;
  private ForgotPassword forgotPassword;
  private UsernameRecovery usernameRecovery;

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
    private final Message message = new Message();
  }

  @Getter
  @Setter
  public static class ForgotPassword {
    private final ForgotPasswordUsername username = new ForgotPasswordUsername();
    private Set<String> allowedPublicIdentityTypes = new LinkedHashSet<>();
    private Map<String, String> tasToPublicIdentityTypeMapping = new LinkedHashMap<>();
    private final Message message = new Message();
    private final Otp otp = new Otp();
    private final ForgotPasswordIdempotency idempotency = new ForgotPasswordIdempotency();
    private final ForgotPasswordDetailsAndOtp detailsAndOtp = new ForgotPasswordDetailsAndOtp();
    private final ResetAuthorization resetAuthorization = new ResetAuthorization();
  }

  @Getter
  @Setter
  public static class ForgotPasswordUsername {
    private int minLength;
    private int maxLength;
    private boolean trim = true;
    private boolean caseInsensitive = true;
    private String normalization = "ASCII_LOWERCASE";
  }

  @Getter
  @Setter
  public static class ForgotPasswordDetailsAndOtp {
    private int mutationLockLeaseSeconds;
    private int mutationLockRedisAllowanceSeconds;
    private int mutationLockSafetyMarginSeconds;
  }

  @Getter
  @Setter
  public static class ForgotPasswordIdempotency {
    private int startTtlSeconds;
    private int detailsAndOtpTtlSeconds;
    private int otpResendTtlSeconds;
    private int otpVerifyTtlSeconds;
    private int completeTtlSeconds;
    private int cancelTtlSeconds;
  }

  @Getter
  @Setter
  public static class ResetAuthorization {
    private int ttlSeconds;
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
    /** Fixed maximum authenticated-session lifetime from creation. */
    private int absoluteTimeoutSeconds;

    /** Sliding authenticated-session inactivity timeout. */
    private int idleTimeoutSeconds;

    /** Redis retention for terminal LOGGED_OUT, REVOKED, and EXPIRED state. */
    private int terminalRetentionSeconds;

    private final AuthenticatedCookie cookie = new AuthenticatedCookie();
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
    private static final String REGISTRATION_PREFIX =
        "auth-workflows.registration.message.destination-override";
    private String mobile;
    private String email;

    public void validateRequiredDestinations() {
      validateRequiredDestinations(REGISTRATION_PREFIX);
    }

    public void validateRequiredDestinations(String propertyPrefix) {
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
          propertyPrefix + " " + "requires non-blank " + missingProperties + " configuration");
    }

    public String requiredMobile() {
      return requiredMobile(REGISTRATION_PREFIX);
    }

    public String requiredMobile(String propertyPrefix) {
      return requiredDestination(mobile, "mobile", propertyPrefix);
    }

    public String requiredEmail() {
      return requiredEmail(REGISTRATION_PREFIX);
    }

    public String requiredEmail(String propertyPrefix) {
      return requiredDestination(email, "email", propertyPrefix);
    }

    private String requiredDestination(
        String destination, String propertyName, String propertyPrefix) {
      if (destination == null || destination.isBlank()) {
        throw new IllegalStateException(propertyPrefix + "." + propertyName + " must not be blank");
      }
      return destination.trim();
    }
  }

  @Getter
  @Setter
  public static class UsernameRecovery {
    private final UsernameRecoverySession session = new UsernameRecoverySession();
    private final Message message = new Message();
  }

  @Getter
  @Setter
  public static class UsernameRecoverySession {
    /** Approved default Username Recovery session TTL in seconds. */
    private int ttlSeconds = 600;
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
    requireRange(
        login.credential.usernameMinLength,
        login.credential.usernameMaxLength,
        "credential username length");
    if (login.credential.usernameAllowedPattern == null
        || login.credential.usernameAllowedPattern.isBlank()) {
      throw new IllegalStateException(
          "auth-workflows.login.credential.username-allowed-pattern must not be blank");
    }
    if (!login.credential.passwordCaseSensitive || login.credential.passwordTrim) {
      throw new IllegalStateException(
          "auth-workflows.login password must remain exact and case-sensitive");
    }
    requirePositive(login.credential.passwordMinLength, "credential.password-min-length");
    requireRange(
        login.credential.passwordMinLength,
        login.credential.passwordMaxLength,
        "credential password length");
    requirePositive(login.otp.length, "otp.length");
    requirePositive(login.otp.ttlSeconds, "otp.ttl-seconds");
    requireNonNegative(login.otp.resendCooldownSeconds, "otp.resend-cooldown-seconds");
    requirePositive(login.otp.maxVerifyAttempts, "otp.max-verify-attempts");
    if (login.otp.smsTemplate == null || !login.otp.smsTemplate.contains("{code}"))
      throw new IllegalStateException("auth-workflows.login.otp.sms-template must contain {code}");
    if (login.otp.emailTemplate.subject == null
        || login.otp.emailTemplate.subject.isBlank()
        || login.otp.emailTemplate.body == null
        || !login.otp.emailTemplate.body.contains("{code}"))
      throw new IllegalStateException("auth-workflows.login.otp.email-template is invalid");
    if (login.otp.resendLimitEnabled) {
      requirePositive(login.otp.maxResendAttempts, "otp.max-resend-attempts");
    }
    requirePositive(login.idempotency.startTtlSeconds, "idempotency.start-ttl-seconds");
    requirePositive(login.idempotency.otpSendTtlSeconds, "idempotency.otp-send-ttl-seconds");
    requirePositive(login.idempotency.otpResendTtlSeconds, "idempotency.otp-resend-ttl-seconds");
    requirePositive(login.idempotency.otpVerifyTtlSeconds, "idempotency.otp-verify-ttl-seconds");
    requirePositive(login.session.absoluteTimeoutSeconds, "session.absolute-timeout-seconds");
    requirePositive(login.session.terminalRetentionSeconds, "session.terminal-retention-seconds");
    requirePositive(login.session.idleTimeoutSeconds, "session.idle-timeout-seconds");
  }

  private void requirePositive(int value, String property) {
    if (value <= 0) {
      throw new IllegalStateException(
          "auth-workflows.login." + property + " must be greater than zero");
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

  public void validateForgotPasswordPolicy() {
    if (forgotPassword == null) {
      throw new IllegalStateException("auth-workflows.forgot-password configuration is required");
    }

    requirePositive(
        forgotPassword.username.minLength, "auth-workflows.forgot-password.username.min-length");
    requireRange(
        forgotPassword.username.minLength,
        forgotPassword.username.maxLength,
        "auth-workflows.forgot-password username length");

    if (forgotPassword.allowedPublicIdentityTypes == null
        || forgotPassword.allowedPublicIdentityTypes.isEmpty()) {
      throw new IllegalStateException(
          "auth-workflows.forgot-password.allowed-public-identity-types must not be empty");
    }
    if (forgotPassword.tasToPublicIdentityTypeMapping == null
        || forgotPassword.tasToPublicIdentityTypeMapping.isEmpty()) {
      throw new IllegalStateException(
          "auth-workflows.forgot-password.tas-to-public-identity-type-mapping must not be empty");
    }

    Set<String> allowedTypes = normalizeAll(forgotPassword.allowedPublicIdentityTypes);
    for (var entry : forgotPassword.tasToPublicIdentityTypeMapping.entrySet()) {
      String tasType = normalize(entry.getKey());
      String publicType = normalize(entry.getValue());
      if (tasType == null || publicType == null) {
        throw new IllegalStateException(
            "auth-workflows.forgot-password.tas-to-public-identity-type-mapping must not contain blank keys or values");
      }
      if (!allowedTypes.contains(publicType)) {
        throw new IllegalStateException(
            "auth-workflows.forgot-password.tas-to-public-identity-type-mapping contains unsupported public identity type: "
                + publicType);
      }
    }

    validateForgotPasswordOtpPolicy(forgotPassword.otp);
    requirePositive(
        forgotPassword.idempotency.startTtlSeconds,
        "auth-workflows.forgot-password.idempotency.start-ttl-seconds");
    requirePositive(
        forgotPassword.idempotency.detailsAndOtpTtlSeconds,
        "auth-workflows.forgot-password.idempotency.details-and-otp-ttl-seconds");
    requirePositive(
        forgotPassword.idempotency.otpResendTtlSeconds,
        "auth-workflows.forgot-password.idempotency.otp-resend-ttl-seconds");
    requirePositive(
        forgotPassword.idempotency.otpVerifyTtlSeconds,
        "auth-workflows.forgot-password.idempotency.otp-verify-ttl-seconds");
    requirePositive(
        forgotPassword.idempotency.completeTtlSeconds,
        "auth-workflows.forgot-password.idempotency.complete-ttl-seconds");
    requirePositive(
        forgotPassword.idempotency.cancelTtlSeconds,
        "auth-workflows.forgot-password.idempotency.cancel-ttl-seconds");
    requirePositive(
        forgotPassword.resetAuthorization.ttlSeconds,
        "auth-workflows.forgot-password.reset-authorization.ttl-seconds");
  }

  public void validateUsernameRecoveryPolicy() {
    if (usernameRecovery == null) {
      throw new IllegalStateException("auth-workflows.username-recovery configuration is required");
    }
    if (usernameRecovery.getSession().getTtlSeconds() <= 0) {
      throw new IllegalStateException(
          "auth-workflows.username-recovery.session.ttl-seconds must be greater than zero");
    }
  }

  private void validateForgotPasswordOtpPolicy(Otp otp) {
    requirePositive(otp.length, "auth-workflows.forgot-password.otp.length");
    requirePositive(otp.ttlSeconds, "auth-workflows.forgot-password.otp.ttl-seconds");
    requireNonNegative(
        otp.resendCooldownSeconds, "auth-workflows.forgot-password.otp.resend-cooldown-seconds");
    requirePositive(
        otp.maxVerifyAttempts, "auth-workflows.forgot-password.otp.max-verify-attempts");
    if (otp.resendLimitEnabled) {
      requirePositive(
          otp.maxResendAttempts, "auth-workflows.forgot-password.otp.max-resend-attempts");
    }
    // requireTemplate(otp.smsTemplate, "auth-workflows.forgot-password.otp.sms-template");
    // if (otp.emailTemplate.subject == null || otp.emailTemplate.subject.isBlank()) {
    //     throw new IllegalStateException(
    //             "auth-workflows.forgot-password.otp.email-template.subject must not be blank");
    // }
    // requireTemplate(otp.emailTemplate.body,
    // "auth-workflows.forgot-password.otp.email-template.body");
  }

  private void requireTemplate(String template, String property) {
    if (template == null || template.isBlank()) {
      throw new IllegalStateException(property + " must not be blank");
    }
    for (String placeholder : List.of("{code}", "{ttlValue}", "{ttlUnit}")) {
      if (!template.contains(placeholder)) {
        throw new IllegalStateException(property + " must contain " + placeholder);
      }
    }
  }

  private Set<String> normalizeAll(Set<String> values) {
    Set<String> normalized = new LinkedHashSet<>();
    for (String value : values) {
      String normalizedValue = normalize(value);
      if (normalizedValue == null) {
        throw new IllegalStateException(
            "auth-workflows.forgot-password.allowed-public-identity-types must not contain blank values");
      }
      normalized.add(normalizedValue);
    }
    return normalized;
  }

  private String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim().toUpperCase();
  }
}

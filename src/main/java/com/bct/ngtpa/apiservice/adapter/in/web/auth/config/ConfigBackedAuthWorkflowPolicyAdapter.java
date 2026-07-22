package com.bct.ngtpa.apiservice.adapter.in.web.auth.config;

import com.bct.ngtpa.apiservice.application.dto.auth.CaptchaPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.OtpEmailPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.OtpPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.PasswordPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.PreStartChallengePolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.PreStartChallengeType;
import com.bct.ngtpa.apiservice.application.dto.auth.RegistrationAuthWorkflowPolicy;
import com.bct.ngtpa.apiservice.application.dto.auth.UsernamePolicy;
import com.bct.ngtpa.apiservice.application.port.out.AuthWorkflowPolicyPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ConfigBackedAuthWorkflowPolicyAdapter implements AuthWorkflowPolicyPort {

    private final AuthWorkflowProperties properties;

    public ConfigBackedAuthWorkflowPolicyAdapter(AuthWorkflowProperties properties) {
        this.properties = properties;
    }

    @Override
    public RegistrationAuthWorkflowPolicy registrationPolicy() {
        var registration = requiredRegistration();

        return new RegistrationAuthWorkflowPolicy(
                new PreStartChallengePolicy(
                        registration.getPreStartChallenge().isEnabled(),
                        toApplicationType(registration.getPreStartChallenge().getType())),
                new CaptchaPolicy(
                        registration.getCaptcha().getLength(),
                        registration.getCaptcha().getAlphabet(),
                        registration.getCaptcha().isCaseSensitive(),
                        registration.getCaptcha().getTtlSeconds(),
                        registration.getCaptcha().getMaxAttempts()),
                new OtpPolicy(
                        registration.getOtp().getLength(),
                        registration.getOtp().isNumericOnly(),
                        registration.getOtp().getTtlSeconds(),
                        registration.getOtp().getResendCooldownSeconds(),
                        registration.getOtp().getMaxVerifyAttempts(),
                        registration.getOtp().isResendLimitEnabled(),
                        registration.getOtp().getMaxResendAttempts(),
                        registration.getOtp().isInvalidatePreviousOnResend(),
                        registration.getOtp().isResetVerifyAttemptsOnResend(),
                        registration.getOtp().getSmsTemplate(),
                        new OtpEmailPolicy(
                                registration.getOtp().getEmailTemplate().getSubject(),
                                registration.getOtp().getEmailTemplate().getBody())),
                new UsernamePolicy(
                        registration.getUsername().getMinLength(),
                        registration.getUsername().getMaxLength(),
                        registration.getUsername().isNoSpaces(),
                        registration.getUsername().isCaseInsensitive(),
                        registration.getUsername().isRequiredDigit(),
                        registration.getUsername().isRequiredSpecial(),
                        registration.getUsername().getAllowedSpecialChars()),
                new PasswordPolicy(
                        registration.getPassword().getMinLength(),
                        registration.getPassword().getMaxLength(),
                        registration.getPassword().isNoSpaces(),
                        registration.getPassword().isNotSameAsUsernameIgnoreCase(),
                        registration.getPassword().getRequiredCategories(),
                        registration.getPassword().getSpecialChars()));
    }

    @Override
    public Optional<String> resolveIdentityTypeSearchScope(String identityType) {
        if (identityType == null) {
            return Optional.empty();
        }

        var normalizedIdentityType = identityType.trim();
        if (normalizedIdentityType.isEmpty()) {
            return Optional.empty();
        }

        return Optional.ofNullable(requiredRegistration().getIdentityTypeMapping().get(normalizedIdentityType))
                .filter(scope -> !scope.isBlank());
    }

    private AuthWorkflowProperties.Registration requiredRegistration() {
        var registration = properties.getRegistration();
        if (registration == null) {
            throw new IllegalStateException("auth-workflows.registration configuration is required");
        }
        return registration;
    }

    private PreStartChallengeType toApplicationType(AuthWorkflowProperties.PreStartChallengeType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case CAPTCHA -> PreStartChallengeType.CAPTCHA;
        };
    }
}
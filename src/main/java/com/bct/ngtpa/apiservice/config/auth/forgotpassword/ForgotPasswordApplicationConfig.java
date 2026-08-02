package com.bct.ngtpa.apiservice.config.auth.forgotpassword;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.config.AuthWorkflowProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationIdentityType;
import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyMode;
import com.bct.ngtpa.apiservice.application.dto.session.SessionConcurrencyPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionOnNewFlowPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;
import com.bct.ngtpa.apiservice.application.port.in.ResendForgotPasswordOtpUseCase;
import com.bct.ngtpa.apiservice.application.port.in.StartForgotPasswordUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordUsernameLookupPort;
import com.bct.ngtpa.apiservice.application.port.out.MessageDeliveryPort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordService;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordDestinationMasker;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordInitialOtpPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpCommitCoordinator;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpReplayResolver;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpTemplates;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ForgotPasswordOtpResendRequestHashGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ResendForgotPasswordOtpService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionConcurrencyService;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import com.bct.ngtpa.apiservice.infrastructure.security.CorsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ForgotPasswordApplicationConfig {
    @Bean
    public Map<String, RegistrationIdentityType> forgotPasswordTasIdentityTypeMapping(
            AuthWorkflowProperties properties) {
        properties.validateForgotPasswordPolicy();
        Map<String, RegistrationIdentityType> mapping = new LinkedHashMap<>();
        properties.getForgotPassword().getTasToPublicIdentityTypeMapping()
                .forEach((tasType, publicType) -> mapping.put(normalize(tasType), toIdentityType(publicType)));
        return Map.copyOf(mapping);
    }

    @Bean
    public StartForgotPasswordUseCase startForgotPasswordUseCase(
            ForgotPasswordIdempotencyPort idempotencyPort,
            ForgotPasswordUsernameLookupPort lookupPort,
            ForgotPasswordAuthFlowContextProtectorPort contextProtector,
            AuthFlowSessionConcurrencyService concurrencyService,
            CreateAuthFlowSessionCsrfUseCase csrfUseCase,
            Clock clock,
            AuthWorkflowProperties workflows,
            AuthFlowSessionProperties sessionProperties,
            Map<String, RegistrationIdentityType> forgotPasswordTasIdentityTypeMapping) {
        workflows.validateForgotPasswordPolicy();
        var scenario = sessionProperties.getScenarios().getForgotPassword();
        var forgotPassword = workflows.getForgotPassword();
        return new StartForgotPasswordService(
                idempotencyPort,
                lookupPort,
                contextProtector,
                concurrencyService,
                csrfUseCase,
                clock,
                new SessionTtlPolicy(scenario.isEnabled(), scenario.isRequired(), scenario.getTtlSeconds()),
                new SessionConcurrencyPolicy(
                        SessionConcurrencyMode.valueOf(sessionProperties.getConcurrency().getMode().name()),
                        SessionOnNewFlowPolicy.valueOf(sessionProperties.getConcurrency().getOnNewFlow().name())),
                sessionProperties.getSessionId().getPrefix(),
                sessionProperties.getSessionId().getEntropyBytes(),
                forgotPassword.getUsername().isTrim(),
                forgotPassword.getUsername().isCaseInsensitive(),
                "ASCII_LOWERCASE".equalsIgnoreCase(forgotPassword.getUsername().getNormalization()),
                forgotPasswordTasIdentityTypeMapping);
    }

    @Bean
    public com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordIdentityUseCase verifyForgotPasswordIdentityUseCase(
            com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextLoader loader,
            ForgotPasswordAuthFlowContextProtectorPort protector,
            com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort sessions,
            com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort locks,
            com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort expiry,
            Clock clock) {
        return new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.identity.VerifyForgotPasswordIdentityService(
                loader, protector, sessions, locks, expiry, clock, java.time.Duration.ofSeconds(10));
    }

    @Bean
    public com.bct.ngtpa.apiservice.application.port.in.VerifyForgotPasswordDetailsUseCase verifyForgotPasswordDetailsUseCase(
            com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort sessions,
            com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort locks,
            com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context.ForgotPasswordProtectedAuthFlowContextLoader loader,
            com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordDetailsLookupPort lookup,
            Clock clock) {
        return new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.VerifyForgotPasswordDetailsService(
                sessions, locks, loader, lookup, clock);
    }

    @Bean
    public com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordOtpSessionBindingPort forgotPasswordOtpSessionBindingPort(
            com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort sessions,
            com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort challenges,
            ForgotPasswordAuthFlowContextProtectorPort protector) {
        return new com.bct.ngtpa.apiservice.adapter.out.session.forgotpassword.ForgotPasswordOtpSessionBindingAdapter(
                sessions, challenges, protector);
    }

    @Bean
    public com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpTemplates forgotPasswordOtpTemplates() {
        return com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.ForgotPasswordOtpTemplates
                .approved();
    }

    @Bean
    public com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordIdentityWebMapper forgotPasswordIdentityWebMapper() {
        return new com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordIdentityWebMapper();
    }

    private static RegistrationIdentityType toIdentityType(String publicType) {
        String normalized = normalize(publicType);
        return RegistrationIdentityType.valueOf("STAFF_NO".equals(normalized) ? "STAFFNO" : normalized);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    @Bean
    public com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpLeasePolicy forgotPasswordDetailsOtpLeasePolicy(
            AuthWorkflowProperties workflows,
            com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties apim,
            java.util.List<com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryTimeoutBound> deliveryTimeoutBounds,
            com.bct.ngtpa.apiservice.adapter.out.redis.config.RedisCacheProperties redis) {
        var d = workflows.getForgotPassword().getDetailsAndOtp();
        int sender = deliveryTimeoutBounds.stream()
                .mapToInt(com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryTimeoutBound::timeoutMillis)
                .max()
                .orElseThrow(() -> new IllegalStateException(
                        "message delivery timeout bounds must be configured"));
        return new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordDetailsOtpLeasePolicy(
                d.getMutationLockLeaseSeconds(), d.getMutationLockRedisAllowanceSeconds(),
                d.getMutationLockSafetyMarginSeconds(),
                apim.getRetry().getBusinessCall().getMaxAttempts(), apim.getTimeoutMilliseconds(),
                apim.getRetry().getBusinessCall().getBackoffMilliseconds(), sender, redis.getTimeoutMilliseconds());
    }

    @Bean
    public com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordUnderLockDetailsVerifier forgotPasswordUnderLockDetailsVerifier(
            com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordDetailsLookupPort p) {
        return new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.detailsotp.ForgotPasswordUnderLockDetailsVerifier(
                p);
    }

    @Bean
    public com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.SecureForgotPasswordOtpCommitRefGenerator forgotPasswordOtpCommitRefGenerator() {
        return new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.SecureForgotPasswordOtpCommitRefGenerator();
    }

    @Bean
    public com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordDetailsOtpWebMapper forgotPasswordDetailsOtpWebMapper() {
        return new com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordDetailsOtpWebMapper();
    }

        @Bean
        public com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordOtpResendWebMapper forgotPasswordOtpResendWebMapper() {
                return new com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper.ForgotPasswordOtpResendWebMapper();
        }

    @Bean
    public com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ForgotPasswordOtpResendPolicy forgotPasswordOtpResendPolicy(
            AuthWorkflowProperties properties) {
        var otp = properties.getForgotPassword().getOtp();
        return new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ForgotPasswordOtpResendPolicy(
                otp.getResendCooldownSeconds(), otp.getMaxResendAttempts());
    }

    @Bean
    public com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ForgotPasswordOtpResendEligibilityValidator forgotPasswordOtpResendEligibilityValidator(
            com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ForgotPasswordOtpResendPolicy policy) {
        return new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ForgotPasswordOtpResendEligibilityValidator(
                policy);
    }

    @Bean
    public com.bct.ngtpa.apiservice.application.port.in.EvaluateForgotPasswordOtpResendUseCase evaluateForgotPasswordOtpResendUseCase(
            com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ForgotPasswordOtpResendEligibilityValidator validator) {
        return new com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.resend.ForgotPasswordOtpResendFoundationService(
                validator);
    }

        @Bean
        public ForgotPasswordInitialOtpPolicy forgotPasswordInitialOtpPolicy(AuthWorkflowProperties properties) {
                var otp = properties.getForgotPassword().getOtp();
                return new ForgotPasswordInitialOtpPolicy(otp.getLength(), otp.isNumericOnly(), otp.getTtlSeconds(),
                                otp.getResendCooldownSeconds(), otp.getMaxVerifyAttempts(), otp.getMaxResendAttempts(),
                                otp.isInvalidatePreviousOnResend());
        }

        @Bean
        public ForgotPasswordDestinationMasker forgotPasswordDestinationMasker() {
                return new ForgotPasswordDestinationMasker();
        }

        @Bean
        public ForgotPasswordOtpResendRequestHashGenerator forgotPasswordOtpResendRequestHashGenerator() {
                return new ForgotPasswordOtpResendRequestHashGenerator();
        }

        @Bean
        public ResendForgotPasswordOtpUseCase resendForgotPasswordOtpUseCase(
                        ForgotPasswordIdempotencyPort idempotency,
                        com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.otp.SecureForgotPasswordOtpCommitRefGenerator refs,
                        com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionMutationLockPort locks,
                        com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort sessions,
                        ForgotPasswordAuthFlowContextProtectorPort protector,
                        com.bct.ngtpa.apiservice.application.port.in.EvaluateForgotPasswordOtpResendUseCase foundation,
                        OtpChallengePort otp,
                        OtpChallengeStatePort challengeState,
                        MessageDeliveryPort delivery,
                        ForgotPasswordDestinationMasker masker,
                        ForgotPasswordOtpCommitCoordinator commits,
                        ForgotPasswordOtpReplayResolver replay,
                        ForgotPasswordOtpTemplates templates,
                        ForgotPasswordInitialOtpPolicy otpPolicy,
                        ForgotPasswordOtpResendRequestHashGenerator hashes,
                        Clock clock,
                        AuthWorkflowProperties properties,
                        CorsProperties corsProperties) {
                properties.validateForgotPasswordPolicy();
                return new ResendForgotPasswordOtpService(
                                idempotency,
                                refs,
                                locks,
                                sessions,
                                protector,
                                foundation,
                                otp,
                                challengeState,
                                delivery,
                                masker,
                                commits,
                                replay,
                                templates,
                                otpPolicy,
                                hashes,
                                clock,
                                Duration.ofSeconds(properties.getForgotPassword().getIdempotency().getOtpResendTtlSeconds()),
                                Duration.ofSeconds(10),
                                corsProperties == null ? List.of() : corsProperties.getAllowedOrigins());
        }

}
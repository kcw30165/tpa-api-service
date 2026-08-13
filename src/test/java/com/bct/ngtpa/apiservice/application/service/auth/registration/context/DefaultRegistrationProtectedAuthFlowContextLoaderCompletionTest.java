package com.bct.ngtpa.apiservice.application.service.auth.registration.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContextAttributeNames;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationAuthFlowContextProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfValidationStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionLifecycleService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.ValidateAuthFlowSessionCsrfResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefaultRegistrationProtectedAuthFlowContextLoaderCompletionTest {

        private static final Instant NOW = Instant.parse("2026-07-14T10:50:00Z");
        private static final AuthFlowSessionId SESSION_ID = new AuthFlowSessionId("afs_safe");

        @Test
        void usernameVerifiedSessionIsValidForCompletionCaller() {
                var expectedContext = context();
                var loader = loader(session(AuthFlowStatus.USERNAME_VERIFIED), expectedContext);
                var command = new RegistrationProtectedAuthFlowContextCommand(
                                SESSION_ID,
                                "csrf-safe",
                                null,
                                null,
                                "req-safe",
                                Set.of(AuthFlowStatus.USERNAME_VERIFIED));

                StepVerifier.create(loader.load(command))
                                .assertNext(result -> {
                                        assertThat(result.status())
                                                        .isEqualTo(RegistrationProtectedAuthFlowContextStatus.VALID);
                                        assertThat(result.registrationContext()).isEqualTo(expectedContext);
                                })
                                .verifyComplete();
        }

        @Test
        void usernameVerifiedSessionRemainsInvalidForLegacyOtpCaller() {
                var loader = loader(session(AuthFlowStatus.USERNAME_VERIFIED), context());
                var legacyOtpCommand = new RegistrationProtectedAuthFlowContextCommand(
                                SESSION_ID,
                                "csrf-safe",
                                null,
                                null,
                                "req-safe");

                StepVerifier.create(loader.load(legacyOtpCommand))
                                .assertNext(result -> assertThat(result.status())
                                                .isEqualTo(RegistrationProtectedAuthFlowContextStatus.INVALID_STATUS))
                                .verifyComplete();
        }

        @Test
        void otpSentSessionIsInvalidForCompletionCaller() {
                var loader = loader(session(AuthFlowStatus.OTP_SENT), context());
                var completionCommand = new RegistrationProtectedAuthFlowContextCommand(
                                SESSION_ID,
                                "csrf-safe",
                                null,
                                null,
                                "req-safe",
                                Set.of(AuthFlowStatus.USERNAME_VERIFIED));

                StepVerifier.create(loader.load(completionCommand))
                                .assertNext(result -> assertThat(result.status())
                                                .isEqualTo(RegistrationProtectedAuthFlowContextStatus.INVALID_STATUS))
                                .verifyComplete();
        }

        private DefaultRegistrationProtectedAuthFlowContextLoader loader(
                        AuthFlowSession session,
                        RegistrationAuthFlowContext context) {
                var lifecycle = mock(AuthFlowSessionLifecycleService.class);
                var csrf = mock(AuthFlowSessionCsrfService.class);
                when(csrf.validateForSession(any())).thenReturn(Mono.just(
                                new ValidateAuthFlowSessionCsrfResult(
                                                AuthFlowSessionCsrfValidationStatus.VALID,
                                                true)));
                when(lifecycle.load(any())).thenReturn(Mono.just(Optional.of(session)));

                RegistrationAuthFlowContextProtectorPort protector = new RegistrationAuthFlowContextProtectorPort() {
                        @Override
                        public RegistrationAuthFlowEncryptedContext protect(
                                        RegistrationAuthFlowContext ignored) {
                                throw new UnsupportedOperationException();
                        }

                        @Override
                        public RegistrationAuthFlowContext unprotect(
                                        RegistrationAuthFlowEncryptedContext ignored) {
                                return context;
                        }
                };

                return new DefaultRegistrationProtectedAuthFlowContextLoader(
                                lifecycle,
                                csrf,
                                protector,
                                Clock.fixed(NOW, ZoneOffset.UTC));
        }

        private AuthFlowSession session(AuthFlowStatus status) {
                var data = new AuthFlowSessionData(
                                "EMAIL",
                                "m****r@example.com",
                                Map.of(
                                                RegistrationAuthFlowContextAttributeNames.CONTEXT_CIPHERTEXT,
                                                "encrypted-registration-context",
                                                RegistrationAuthFlowContextAttributeNames.CONTEXT_KEY_VERSION,
                                                "local-v1",
                                                RegistrationAuthFlowContextAttributeNames.CONTEXT_ALGORITHM,
                                                "AES_GCM_V1"));
                return new AuthFlowSession(
                                SESSION_ID,
                                AuthFlowScenario.REGISTRATION,
                                status,
                                "csrf-hash",
                                "otp_safe",
                                data,
                                NOW.minusSeconds(120),
                                NOW.minusSeconds(60),
                                NOW.plusSeconds(1200));
        }

        private RegistrationAuthFlowContext context() {
                return new RegistrationAuthFlowContext(
                                MemberIdentityType.GPFS,
                                "identity-safe",
                                "31/01/1980",
                                RegistrationContactType.EMAIL,
                                "member@example.com");
        }
}

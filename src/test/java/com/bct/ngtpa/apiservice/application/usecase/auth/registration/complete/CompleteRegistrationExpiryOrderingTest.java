package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyKey;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCancellationPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionCompletionLockPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationCompletionPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationLookupPort;
import com.bct.ngtpa.apiservice.application.service.auth.idempotency.RegistrationRequestHashGenerator;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextResult;
import com.bct.ngtpa.apiservice.application.service.auth.registration.context.RegistrationProtectedAuthFlowContextStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CompleteRegistrationExpiryOrderingTest {

    private static final Instant NOW = Instant.parse("2026-07-14T10:50:00Z");
    private static final AuthFlowSessionId SESSION_ID = new AuthFlowSessionId("afs_safe");

    @Test
    void acceptedUsernameVerifiedContextRefreshesExpiryBeforeFurtherProcessing() {
        var contextLoader = mock(RegistrationProtectedAuthFlowContextLoader.class);
        var expiry = mock(AuthFlowSessionExpiryRefreshPort.class);
        var marker = new IllegalStateException("stop-after-expiry");
        when(contextLoader.load(any())).thenReturn(Mono.just(
                RegistrationProtectedAuthFlowContextResult.valid(
                        "otp-safe",
                        "m****r@example.com",
                        registrationContext())));
        when(expiry.refresh(eq(SESSION_ID), eq(NOW))).thenReturn(Mono.error(marker));
        var service = service(contextLoader, expiry);

        StepVerifier.create(service.complete(command("csrf-safe")))
                .expectErrorMatches(error -> error == marker)
                .verify();

        verify(expiry).refresh(SESSION_ID, NOW);
    }

    @Test
    void csrfRejectedRequestDoesNotRefreshExpiry() {
        var contextLoader = mock(RegistrationProtectedAuthFlowContextLoader.class);
        var expiry = mock(AuthFlowSessionExpiryRefreshPort.class);
        when(contextLoader.load(any())).thenReturn(Mono.just(
                RegistrationProtectedAuthFlowContextResult.invalid(
                        RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID)));
        var service = service(contextLoader, expiry);

        StepVerifier.create(service.complete(command("bad-csrf")))
                .assertNext(result -> {
                    assertThat(result.status())
                            .isEqualTo(CompleteRegistrationResultStatus.CSRF_INVALID);
                    assertThat(result.errors())
                            .extracting(error -> error.code())
                            .containsExactly("registration.authFlow.invalid");
                })
                .verifyComplete();

        verify(expiry, never()).refresh(any(), any());
    }

    @Test
    void missingOrInvalidProtectedContextDoesNotRefreshExpiry() {
        var contextLoader = mock(RegistrationProtectedAuthFlowContextLoader.class);
        var expiry = mock(AuthFlowSessionExpiryRefreshPort.class);
        when(contextLoader.load(any())).thenReturn(Mono.just(
                RegistrationProtectedAuthFlowContextResult.invalid(
                        RegistrationProtectedAuthFlowContextStatus.INVALID_STATUS)));
        var service = service(contextLoader, expiry);

        StepVerifier.create(service.complete(command("csrf-safe")))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(CompleteRegistrationResultStatus.INVALID_AUTH_FLOW))
                .verifyComplete();

        verify(expiry, never()).refresh(any(), any());
    }

    private CompleteRegistrationService service(
            RegistrationProtectedAuthFlowContextLoader contextLoader,
            AuthFlowSessionExpiryRefreshPort expiry) {
        return new CompleteRegistrationService(
                contextLoader,
                mock(AuthFlowSessionPort.class),
                mock(RegistrationUsernameCheckContextLoader.class),
                mock(RegistrationPasswordValidator.class),
                mock(RegistrationIdempotencyPort.class),
                mock(RegistrationRequestHashGenerator.class),
                mock(AuthFlowSessionCompletionLockPort.class),
                expiry,
                mock(RegistrationCompletionPort.class),
                mock(RegistrationLookupPort.class),
                mock(AuthFlowSessionCancellationPort.class),
                Duration.ofSeconds(30),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private CompleteRegistrationCommand command(String csrfToken) {
        return new CompleteRegistrationCommand(
                SESSION_ID,
                csrfToken,
                new RegistrationIdempotencyKey("idem-safe"),
                "Password1!",
                "Password1!",
                null,
                null,
                "en",
                "req-safe",
                Duration.ofMinutes(5));
    }

    private RegistrationAuthFlowContext registrationContext() {
        return new RegistrationAuthFlowContext(
                RegistrationIdentityType.GPFS,
                "identity-safe",
                "31/01/1980",
                RegistrationContactType.EMAIL,
                "member@example.com");
    }
}

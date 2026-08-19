package com.bct.ngtpa.apiservice.application.service.auth.registration.context;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionExpiryRefreshPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SlidingRegistrationProtectedAuthFlowContextLoaderTest {
  private static final Instant NOW = Instant.parse("2026-07-14T10:50:00Z");
  private static final AuthFlowSessionId SESSION_ID = new AuthFlowSessionId("afs_safe");

  @Test
  void validOtpInteractionRefreshesBeforeReturningContext() {
    var delegate = mock(RegistrationProtectedAuthFlowContextLoader.class);
    var expiry = mock(AuthFlowSessionExpiryRefreshPort.class);
    var result = RegistrationProtectedAuthFlowContextResult.valid("otp-safe", "masked", null);
    when(delegate.load(any())).thenReturn(Mono.just(result));
    when(expiry.refresh(SESSION_ID, NOW))
        .thenReturn(Mono.error(new IllegalStateException("refresh-stop")));
    var loader =
        new SlidingRegistrationProtectedAuthFlowContextLoader(
            delegate, expiry, Clock.fixed(NOW, ZoneOffset.UTC));

    StepVerifier.create(loader.load(command(Set.of(AuthFlowStatus.OTP_SENT))))
        .expectErrorMessage("refresh-stop")
        .verify();
    verify(expiry).refresh(SESSION_ID, NOW);
  }

  @Test
  void invalidSecurityContextDoesNotRefresh() {
    var delegate = mock(RegistrationProtectedAuthFlowContextLoader.class);
    var expiry = mock(AuthFlowSessionExpiryRefreshPort.class);
    when(delegate.load(any()))
        .thenReturn(
            Mono.just(
                RegistrationProtectedAuthFlowContextResult.invalid(
                    RegistrationProtectedAuthFlowContextStatus.CSRF_INVALID)));
    var loader =
        new SlidingRegistrationProtectedAuthFlowContextLoader(
            delegate, expiry, Clock.fixed(NOW, ZoneOffset.UTC));

    StepVerifier.create(loader.load(command(Set.of(AuthFlowStatus.OTP_SENT))))
        .expectNextCount(1)
        .verifyComplete();
    verify(expiry, never()).refresh(any(), any());
  }

  @Test
  void completionStatusIsNotDoubleRefreshedByOtpDecorator() {
    var delegate = mock(RegistrationProtectedAuthFlowContextLoader.class);
    var expiry = mock(AuthFlowSessionExpiryRefreshPort.class);
    when(delegate.load(any()))
        .thenReturn(
            Mono.just(
                RegistrationProtectedAuthFlowContextResult.valid("otp-safe", "masked", null)));
    var loader =
        new SlidingRegistrationProtectedAuthFlowContextLoader(
            delegate, expiry, Clock.fixed(NOW, ZoneOffset.UTC));

    StepVerifier.create(loader.load(command(Set.of(AuthFlowStatus.USERNAME_VERIFIED))))
        .expectNextCount(1)
        .verifyComplete();
    verify(expiry, never()).refresh(any(), any());
  }

  private RegistrationProtectedAuthFlowContextCommand command(Set<AuthFlowStatus> statuses) {
    return new RegistrationProtectedAuthFlowContextCommand(
        SESSION_ID, "csrf-safe", null, null, "req-safe", statuses);
  }
}

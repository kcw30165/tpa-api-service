package com.bct.ngtpa.apiservice.application.service.auth.forgotpassword.context;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
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

class SlidingForgotPasswordProtectedAuthFlowContextLoaderTest {
  private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");
  private static final AuthFlowSessionId SESSION_ID = new AuthFlowSessionId("afs_fp");

  @Test
  void refreshesExpiryOnlyAfterSuccessfulValidation() {
    var delegate = mock(ForgotPasswordProtectedAuthFlowContextLoader.class);
    var expiry = mock(AuthFlowSessionExpiryRefreshPort.class);
    when(delegate.load(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordProtectedAuthFlowContextResult.valid(
                    "otp-fp",
                    new ForgotPasswordAuthFlowContext(
                        "member01",
                        "tas-user-01",
                        "ID",
                        true,
                        "reset-subject-01",
                        "EMAIL",
                        "member@example.com",
                        "m****r@example.com",
                        null,
                        null,
                        null,
                        null,
                        "v1",
                        null))));
    when(expiry.refresh(SESSION_ID, NOW))
        .thenReturn(Mono.error(new IllegalStateException("refresh-stop")));
    var loader =
        new SlidingForgotPasswordProtectedAuthFlowContextLoader(
            delegate, expiry, Clock.fixed(NOW, ZoneOffset.UTC));

    StepVerifier.create(loader.load(command())).expectErrorMessage("refresh-stop").verify();
    verify(expiry).refresh(SESSION_ID, NOW);
  }

  @Test
  void invalidResultDoesNotRefresh() {
    var delegate = mock(ForgotPasswordProtectedAuthFlowContextLoader.class);
    var expiry = mock(AuthFlowSessionExpiryRefreshPort.class);
    when(delegate.load(any()))
        .thenReturn(
            Mono.just(
                ForgotPasswordProtectedAuthFlowContextResult.invalid(
                    ForgotPasswordProtectedAuthFlowContextStatus.CSRF_INVALID)));
    var loader =
        new SlidingForgotPasswordProtectedAuthFlowContextLoader(
            delegate, expiry, Clock.fixed(NOW, ZoneOffset.UTC));

    StepVerifier.create(loader.load(command())).expectNextCount(1).verifyComplete();
    verify(expiry, never()).refresh(any(), any());
  }

  private ForgotPasswordProtectedAuthFlowContextCommand command() {
    return new ForgotPasswordProtectedAuthFlowContextCommand(
        SESSION_ID,
        "csrf-token",
        null,
        null,
        "corr-1",
        Set.of(AuthFlowStatus.IDENTITY_VERIFIED),
        true);
  }
}

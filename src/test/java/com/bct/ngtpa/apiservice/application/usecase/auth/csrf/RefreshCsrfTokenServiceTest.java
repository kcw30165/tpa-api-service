package com.bct.ngtpa.apiservice.application.usecase.auth.csrf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.csrf.CsrfRefreshSessionType;
import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenStatus;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidator;
import com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidationResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Branch-oriented reactive tests for {@link RefreshCsrfTokenService}. */
class RefreshCsrfTokenServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final String ALLOWED_ORIGIN = "https://portal.bcthk.com";

  private final AuthenticatedSessionCsrfRefreshService authenticatedRefresh =
      mock(AuthenticatedSessionCsrfRefreshService.class);
  private final PreAuthSessionCsrfRefreshService preAuthRefresh =
      mock(PreAuthSessionCsrfRefreshService.class);
  private final OriginRefererValidator provenanceValidator = mock(OriginRefererValidator.class);

  private RefreshCsrfTokenService newService() {
    return new RefreshCsrfTokenService(
        authenticatedRefresh, preAuthRefresh, provenanceValidator, List.of(ALLOWED_ORIGIN), clock);
  }

  private RefreshCsrfTokenCommand command(String authenticatedId, String authFlowId) {
    return new RefreshCsrfTokenCommand(
        authenticatedId, authFlowId, ALLOWED_ORIGIN, ALLOWED_ORIGIN + "/page", "req-1");
  }

  private void givenOriginValid() {
    when(provenanceValidator.validate(any(), any(), any()))
        .thenReturn(OriginRefererValidationResult.success());
  }

  // ----- origin gate --------------------------------------------------------

  @Test
  void invalidOriginYieldsOriginRejected() {
    when(provenanceValidator.validate(any(), any(), any()))
        .thenReturn(OriginRefererValidationResult.failure(com.bct.ngtpa.apiservice.application.service.security.csrf.OriginRefererValidationStatus.ORIGIN_NOT_ALLOWED));
    StepVerifier.create(newService().refresh(command("sid_abcdefghijklmnopqrstuvwx", null)))
        .assertNext(r -> assertThat(r.status()).isEqualTo(RefreshCsrfTokenStatus.ORIGIN_REJECTED))
        .verifyComplete();
  }

  // ----- authenticated-session path -------------------------------------------

  @Test
  void authenticatedSessionSuccessDelegates() {
    givenOriginValid();
    when(authenticatedRefresh.refresh(any(), any()))
        .thenReturn(
            Mono.just(
                AuthenticatedSessionCsrfRefreshService.Outcome.success("raw-token")));
    StepVerifier.create(newService().refresh(command("sid_abcdefghijklmnopqrstuvwx", null)))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(RefreshCsrfTokenStatus.SUCCESS);
              assertThat(r.sessionType()).isEqualTo(CsrfRefreshSessionType.AUTHENTICATED);
              assertThat(r.csrfToken()).isEqualTo("raw-token");
            })
        .verifyComplete();
  }

  @Test
  void authenticatedSessionFailureDelegates() {
    givenOriginValid();
    when(authenticatedRefresh.refresh(any(), any()))
        .thenReturn(
            Mono.just(
                AuthenticatedSessionCsrfRefreshService.Outcome.failure(
                    RefreshCsrfTokenStatus.SESSION_MISSING)));
    StepVerifier.create(newService().refresh(command("sid_abcdefghijklmnopqrstuvwx", null)))
        .assertNext(r -> assertThat(r.status()).isEqualTo(RefreshCsrfTokenStatus.SESSION_MISSING))
        .verifyComplete();
  }

  // ----- pre-auth path --------------------------------------------------------

  @Test
  void bothSessionIdsBlankYieldsSessionMissing() {
    givenOriginValid();
    StepVerifier.create(newService().refresh(command(null, null)))
        .assertNext(r -> assertThat(r.status()).isEqualTo(RefreshCsrfTokenStatus.SESSION_MISSING))
        .verifyComplete();
  }

  @Test
  void preAuthSuccessDelegates() {
    givenOriginValid();
    when(preAuthRefresh.refresh(any(), any()))
        .thenReturn(Mono.just(PreAuthSessionCsrfRefreshService.Outcome.success("raw-token-2")));
    StepVerifier.create(newService().refresh(command(null, "afs-1")))
        .assertNext(
            r -> {
              assertThat(r.status()).isEqualTo(RefreshCsrfTokenStatus.SUCCESS);
              assertThat(r.sessionType()).isEqualTo(CsrfRefreshSessionType.PRE_AUTH);
            })
        .verifyComplete();
  }

  @Test
  void preAuthFailureDelegates() {
    givenOriginValid();
    when(preAuthRefresh.refresh(any(), any()))
        .thenReturn(
            Mono.just(
                PreAuthSessionCsrfRefreshService.Outcome.failure(
                    RefreshCsrfTokenStatus.SESSION_INVALID)));
    StepVerifier.create(newService().refresh(command(null, "afs-1")))
        .assertNext(r -> assertThat(r.status()).isEqualTo(RefreshCsrfTokenStatus.SESSION_INVALID))
        .verifyComplete();
  }
}

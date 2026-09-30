package com.bct.ngtpa.apiservice.application.usecase.auth.csrf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.csrf.RefreshCsrfTokenStatus;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CreateAuthFlowSessionCsrfResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Branch-oriented reactive tests for {@link PreAuthSessionCsrfRefreshService}. */
class PreAuthSessionCsrfRefreshServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final AuthFlowSessionCsrfService delegate = mock(AuthFlowSessionCsrfService.class);

  private PreAuthSessionCsrfRefreshService newService() {
    return new PreAuthSessionCsrfRefreshService(delegate, clock);
  }

  @Test
  void blankSessionIdYieldsSessionInvalid() {
    StepVerifier.create(newService().refresh("", "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SESSION_INVALID))
        .verifyComplete();
  }

  @Test
  void successDelegatesToken() {
    when(delegate.rotateForSession(any()))
        .thenReturn(
            Mono.just(
                new CreateAuthFlowSessionCsrfResult("raw-token", NOW.plusSeconds(1800), 1800)));
    StepVerifier.create(newService().refresh("afs-preauth-1", "req-1"))
        .assertNext(
            o -> {
              assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SUCCESS);
              assertThat(o.csrfToken()).isEqualTo("raw-token");
            })
        .verifyComplete();
  }

  @Test
  void sessionNotAvailableYieldsSessionMissing() {
    when(delegate.rotateForSession(any()))
        .thenReturn(Mono.error(new IllegalStateException("Auth flow session not available")));
    StepVerifier.create(newService().refresh("afs-preauth-1", "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SESSION_MISSING))
        .verifyComplete();
  }

  @Test
  void sessionNotUsableYieldsSessionNotUsable() {
    when(delegate.rotateForSession(any()))
        .thenReturn(Mono.error(new IllegalStateException("Auth flow session is not usable")));
    StepVerifier.create(newService().refresh("afs-preauth-1", "req-1"))
        .assertNext(
            o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SESSION_NOT_USABLE))
        .verifyComplete();
  }

  @Test
  void revisionConflictYieldsConflict() {
    when(delegate.rotateForSession(any()))
        .thenReturn(Mono.error(new IllegalStateException("revision conflict detected")));
    StepVerifier.create(newService().refresh("afs-preauth-1", "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.CONFLICT))
        .verifyComplete();
  }

  @Test
  void causeChainConflictYieldsConflict() {
    RuntimeException cause = new RuntimeException("concurrent mutation");
    when(delegate.rotateForSession(any()))
        .thenReturn(Mono.error(new IllegalStateException("wrapped", cause)));
    StepVerifier.create(newService().refresh("afs-preauth-1", "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.CONFLICT))
        .verifyComplete();
  }

  @Test
  void unrecognizedErrorYieldsSystemFailure() {
    when(delegate.rotateForSession(any()))
        .thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newService().refresh("afs-preauth-1", "req-1"))
        .assertNext(o -> assertThat(o.status()).isEqualTo(RefreshCsrfTokenStatus.SYSTEM_FAILURE))
        .verifyComplete();
  }
}

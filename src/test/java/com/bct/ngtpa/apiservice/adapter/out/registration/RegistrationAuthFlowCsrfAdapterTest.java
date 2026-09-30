package com.bct.ngtpa.apiservice.adapter.out.registration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.usecase.session.authflow.AuthFlowSessionCsrfService;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CreateAuthFlowSessionCsrfResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Branch-oriented reactive tests for {@link RegistrationAuthFlowCsrfAdapter}. */
class RegistrationAuthFlowCsrfAdapterTest {

  private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final AuthFlowSessionCsrfService csrfService = mock(AuthFlowSessionCsrfService.class);

  private RegistrationAuthFlowCsrfAdapter newAdapter() {
    return new RegistrationAuthFlowCsrfAdapter(csrfService, clock, 1800);
  }

  // ----- constructor guards ----------------------------------------------------

  @Test
  void nullCsrfServiceThrows() {
    assertThatThrownBy(() -> new RegistrationAuthFlowCsrfAdapter(null, clock, 1800))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void nullClockThrows() {
    assertThatThrownBy(() -> new RegistrationAuthFlowCsrfAdapter(csrfService, null, 1800))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void nonPositiveTtlThrows() {
    assertThatThrownBy(() -> new RegistrationAuthFlowCsrfAdapter(csrfService, clock, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ----- bindCsrfToken(): success via real accessor (CreateAuthFlowSessionCsrfResult.rawToken())
  // --

  @Test
  void bindCsrfTokenExtractsRawTokenFromRealResult() {
    when(csrfService.createForSession(any()))
        .thenReturn(
            Mono.just(
                new CreateAuthFlowSessionCsrfResult("raw-token-123", NOW.plusSeconds(1800), 1800)));
    StepVerifier.create(newAdapter().bindCsrfToken("afs-reg-1"))
        .expectNext("raw-token-123")
        .verifyComplete();
  }

  // NOTE (round 116): there is intentionally no test exercising extractTokenValue's
  // fallback accessor names ("token"/"csrfToken"/"value") or its "no matching
  // accessor" IllegalStateException branch. Confirmed via the real production
  // source (round 115): AuthFlowSessionCsrfService.createForSession(...) is
  // declared to return Mono<CreateAuthFlowSessionCsrfResult> - a CONCRETE type.
  // Java's generic erasure inserts an implicit checked cast to
  // CreateAuthFlowSessionCsrfResult at the .map(...) call site inside
  // bindCsrfToken(...), so no test can make this call site receive any object
  // OTHER than a real CreateAuthFlowSessionCsrfResult - which always has a
  // rawToken() accessor that always matches on the FIRST reflective attempt.
  // The fallback accessors and the "no matching accessor" branch are therefore
  // DEAD CODE, unreachable via the real type system (same category as the
  // §9b/§9c dead-code findings recorded elsewhere in
  // docs/coverage/code-coverage-95-plan.md).

  @Test
  void bindCsrfTokenServiceErrorPropagates() {
    when(csrfService.createForSession(any()))
        .thenReturn(Mono.error(new IllegalStateException("boom")));
    StepVerifier.create(newAdapter().bindCsrfToken("afs-reg-1"))
        .expectError(IllegalStateException.class)
        .verify();
  }
}

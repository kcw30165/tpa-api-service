package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartOperationHarness.NOW;
import static com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartOperationHarness.matched;
import static com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartOperationHarness.registered;
import static com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.UsernameRecoveryStartOperationHarness.unregistered;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCandidate;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoverySchemeProjection;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContext;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyResponseEnvelope;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayState;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartReplayCodec;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Behaviour that must NOT change: registered-only and mixed lists, member-not-found, lookup
 * failures, the strict SESSION_CREATED result invariants and the registered completion rule.
 */
class UsernameRecoveryStartRegisteredAndNotFoundPreservationTest {

  @Test
  void registeredOnlyListStillCreatesTheSession() {
    var harness = new UsernameRecoveryStartOperationHarness();
    harness.lookupReturns(matched(registered("DB", "DBA", "user-1")));

    StepVerifier.create(harness.execute())
        .assertNext(
            result -> {
              assertThat(result.outcome())
                  .isEqualTo(UsernameRecoveryStartResult.Outcome.SESSION_CREATED);
              assertThat(result.schemes())
                  .singleElement()
                  .satisfies(scheme -> assertThat(scheme.registered()).isTrue());
            })
        .verifyComplete();
    assertThat(harness.calls).containsExactly("captcha", "lookup", "session");
  }

  @Test
  void mixedListStillCreatesTheSessionAndKeepsTheUnregisteredFlag() {
    var harness = new UsernameRecoveryStartOperationHarness();
    harness.lookupReturns(matched(registered("DB", "DBA", "user-1"), unregistered("DB", "DBB")));

    StepVerifier.create(harness.execute())
        .assertNext(
            result -> {
              assertThat(result.outcome())
                  .isEqualTo(UsernameRecoveryStartResult.Outcome.SESSION_CREATED);
              assertThat(result.schemes())
                  .extracting(UsernameRecoverySchemeProjection::registered)
                  .containsExactly(true, false);
              assertThat(result.schemes())
                  .allSatisfy(scheme -> assertThat(scheme.schemeRef()).isNotNull());
            })
        .verifyComplete();
  }

  @Test
  void memberNotFoundStillCreatesNoSession() {
    var harness = new UsernameRecoveryStartOperationHarness();
    harness.lookupReturns(
        new UsernameRecoveryLookupResult(
            UsernameRecoveryLookupResult.Status.NOT_FOUND, List.of()));

    StepVerifier.create(harness.execute())
        .assertNext(
            result -> {
              assertThat(result.outcome())
                  .isEqualTo(UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND);
              assertThat(result.authFlowSessionId()).isNull();
              assertThat(result.csrfToken()).isNull();
              assertThat(result.schemes()).isEmpty();
            })
        .verifyComplete();
    assertThat(harness.calls).containsExactly("captcha", "lookup");
    assertThat(harness.stored.get()).isNull();
    assertThat(harness.creates.get()).isZero();
  }

  @Test
  void anEmptyMatchedLookupCanNeverBeBuiltSoItCanNeverBecomeASuccess() {
    List<UsernameRecoveryLookupCandidate> none = List.of();
    assertThatThrownBy(
            () ->
                new UsernameRecoveryLookupResult(
                    UsernameRecoveryLookupResult.Status.MATCHED, none))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("matched lookup requires at least one candidate");
  }

  @Test
  void lookupFailureStatusesStillNeverCreateASession() {
    for (var status :
        List.of(
            UsernameRecoveryLookupResult.Status.CONTRACT_INVALID,
            UsernameRecoveryLookupResult.Status.UNAVAILABLE,
            UsernameRecoveryLookupResult.Status.UNKNOWN_OUTCOME)) {
      var harness = new UsernameRecoveryStartOperationHarness();
      harness.lookupReturns(new UsernameRecoveryLookupResult(status, List.of()));
      StepVerifier.create(harness.execute())
          .expectError(UsernameRecoveryStartException.class)
          .verify();
      assertThat(harness.stored.get()).isNull();
    }
  }

  @Test
  void sessionCreatedResultStillRequiresCsrfExpiryAndANonEmptySchemeList() {
    var registeredProjection =
        new UsernameRecoverySchemeProjection(
            new UsernameRecoverySchemeRef("sch_1"), "DB", "DBA", true);
    var oneRegistered = List.of(registeredProjection);
    assertThatThrownBy(
            () ->
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
                    null,
                    null,
                    600,
                    oneRegistered))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("session-created result requires a nonblank csrfToken");
    assertThatThrownBy(
            () ->
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.SESSION_CREATED,
                    null,
                    "csrf",
                    0,
                    oneRegistered))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("session-created result requires a positive expiresInSeconds");
    List<UsernameRecoverySchemeProjection> none = List.of();
    assertThatThrownBy(
            () ->
                new UsernameRecoveryStartResult(
                    UsernameRecoveryStartResult.Outcome.SESSION_CREATED, null, "csrf", 600, none))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("session-created result requires at least one scheme projection");
  }

  @Test
  void registeredCompletionRuleOfTheProtectedContextIsUnchanged() {
    var destination =
        new UsernameRecoveryVerifiedDestination(
            "member@example.com", UsernameRecoveryVerifiedDestination.Channel.EMAIL);
    var allRequested =
        List.of(candidate("sch_r1", "DBA", true, "user-1", UsernameRecoveryDeliveryState.REQUESTED));
    assertThatThrownBy(
            () ->
                new UsernameRecoveryAuthFlowContext(
                    UsernameRecoveryWorkflowStatus.ACTIVE,
                    destination,
                    Locale.ENGLISH,
                    allRequested))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("candidate state and workflow completion must agree");

    var oneOutstanding =
        List.of(
            candidate("sch_r1", "DBA", true, "user-1", UsernameRecoveryDeliveryState.REQUESTED),
            candidate("sch_r2", "DBB", true, "user-2", UsernameRecoveryDeliveryState.AVAILABLE));
    assertThatThrownBy(
            () ->
                new UsernameRecoveryAuthFlowContext(
                    UsernameRecoveryWorkflowStatus.COMPLETED,
                    destination,
                    Locale.ENGLISH,
                    oneOutstanding))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("candidate state and workflow completion must agree");

    List<UsernameRecoverySessionCandidate> none = List.of();
    assertThatThrownBy(
            () ->
                new UsernameRecoveryAuthFlowContext(
                    UsernameRecoveryWorkflowStatus.ACTIVE, destination, Locale.ENGLISH, none))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void memberNotFoundReplayStillRoundTripsWithoutProtectedStorage() {
    var port = new NoopReplayPort();
    var codec =
        new UsernameRecoveryStartReplayCodec(
            new UsernameRecoveryIdempotencyResponseEnvelope(
                new JacksonTransientJsonAdapter(new ObjectMapper())),
            port,
            Clock.fixed(NOW, ZoneOffset.UTC));
    var result =
        UsernameRecoveryStartResult.withoutSession(
            UsernameRecoveryStartResult.Outcome.MEMBER_NOT_FOUND, List.of());

    var captured =
        codec
            .capture(
                result,
                new HydratedIdempotentResponse(
                    200, "application/json", "{\"first\":true}", Map.of()),
                Duration.ofMinutes(5))
            .block();

    assertThat(captured.snapshot().protectedReplayReference()).isNull();
    assertThat(codec.hydrate(captured.snapshot()).block()).isEqualTo(result);
    assertThat(port.resolves).isZero();
  }

  private static UsernameRecoverySessionCandidate candidate(
      String ref,
      String trustCode,
      boolean registered,
      String userName,
      UsernameRecoveryDeliveryState state) {
    boolean requested = state == UsernameRecoveryDeliveryState.REQUESTED;
    return new UsernameRecoverySessionCandidate(
        new UsernameRecoveryCandidate(
            new UsernameRecoverySchemeRef(ref), "DB", trustCode, registered, userName, state, null, null),
        requested ? NOW : null,
        requested ? "sender-ref" : null);
  }

  private static final class NoopReplayPort implements UsernameRecoveryStartProtectedReplayPort {
    private int resolves;

    @Override
    public Mono<ProtectedReplayReference> store(
        UsernameRecoveryStartProtectedReplayState state, Duration ttl) {
      return Mono.error(new AssertionError("non-session replay must not use protected storage"));
    }

    @Override
    public Mono<UsernameRecoveryStartResult> resolve(ProtectedReplayReference reference) {
      resolves++;
      return Mono.empty();
    }
  }
}

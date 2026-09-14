package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryDestinationType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestResult;
import com.bct.ngtpa.apiservice.application.idempotency.HydratedIdempotentResponse;
import com.bct.ngtpa.apiservice.application.idempotency.IdempotentTerminalResponseSnapshot;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.UsernameRecoveryIdempotencyResponseEnvelope;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UsernameRecoveryRequestReplayCodecTest {
  private final JacksonTransientJsonAdapter json =
      new JacksonTransientJsonAdapter(new ObjectMapper());
  private final UsernameRecoveryRequestReplayCodec codec =
      new UsernameRecoveryRequestReplayCodec(new UsernameRecoveryIdempotencyResponseEnvelope(json));

  @Test
  void everyDeterministicOutcomeRoundTripsWithoutProtectedReference() {
    List<UsernameRecoveryRequestResult> results =
        List.of(
            accepted(),
            simple(UsernameRecoveryRequestResult.Outcome.ALREADY_REQUESTED),
            simple(UsernameRecoveryRequestResult.Outcome.WORKFLOW_COMPLETED),
            simple(UsernameRecoveryRequestResult.Outcome.UNREGISTERED_CANDIDATE),
            simple(UsernameRecoveryRequestResult.Outcome.INVALID_SCHEME_REF),
            simple(UsernameRecoveryRequestResult.Outcome.SYSTEM_FAILED));
    for (var result : results) {
      var captured = codec.capture(result, response(result)).block();
      assertThat(captured.response()).isEqualTo(response(result));
      assertThat(captured.snapshot().protectedReplayReference()).isNull();
      assertThat(codec.hydrate(captured.snapshot()).block()).isEqualTo(result);
    }
  }

  @Test
  void snapshotContainsApprovedProjectionOnly() {
    String body =
        codec.capture(accepted(), response(accepted())).block().snapshot().serializedSafeBody();
    assertThat(body)
        .contains(
            "REQUEST_ACCEPTED",
            "PARTIALLY_REQUESTED",
            "sch_opaque-1",
            "p***@example.test",
            "EMAIL");
    assertThat(body)
        .doesNotContain(
            "authFlowSession", "username", "csrf", "correlation", "ownership", "messageBody");
  }

  @Test
  void unknownAndUnsafeTransportAreRejected() {
    assertThatThrownBy(
            () ->
                codec
                    .capture(
                        simple(UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME),
                        response(simple(UsernameRecoveryRequestResult.Outcome.UNKNOWN_OUTCOME)))
                    .block())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                codec
                    .capture(
                        accepted(),
                        new HydratedIdempotentResponse(
                            200,
                            "application/json",
                            json.write(accepted()),
                            Map.of("Retry-After", "1")))
                    .block())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                codec
                    .capture(
                        accepted(),
                        new HydratedIdempotentResponse(
                            200,
                            "application/json",
                            json.write(accepted()),
                            Map.of(),
                            new ProtectedReplayReference("requestProtectedReference01")))
                    .block())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void malformedWrongActionContradictoryUnknownFieldAndProtectedSnapshotFailClosed() {
    assertInvalid(
        "{\"idempotencyAction\":\"start\",\"payload\":{\"outcome\":\"SYSTEM_FAILED\"}}", null);
    assertInvalid("{\"idempotencyAction\":\"request\",\"payload\":{\"outcome\":\"BOGUS\"}}", null);
    assertInvalid(
        "{\"idempotencyAction\":\"request\",\"payload\":{\"outcome\":\"SYSTEM_FAILED\",\"schemeRef\":\"sch_bad\"}}",
        null);
    assertInvalid(
        "{\"idempotencyAction\":\"request\",\"payload\":{\"outcome\":\"SYSTEM_FAILED\",\"extra\":true}}",
        null);
    assertInvalid(
        "{\"idempotencyAction\":\"request\",\"payload\":{\"outcome\":\"SYSTEM_FAILED\"}}",
        new ProtectedReplayReference("requestProtectedReference01"));
  }

  private void assertInvalid(String body, ProtectedReplayReference reference) {
    var snapshot =
        new IdempotentTerminalResponseSnapshot(
            1, 200, "application/json", body, Map.of(), reference);
    assertThatThrownBy(() -> codec.hydrate(snapshot).block())
        .isInstanceOf(IllegalArgumentException.class);
  }

  private HydratedIdempotentResponse response(UsernameRecoveryRequestResult r) {
    return new HydratedIdempotentResponse(200, "application/json", json.write(r), Map.of());
  }

  private static UsernameRecoveryRequestResult accepted() {
    return new UsernameRecoveryRequestResult(
        UsernameRecoveryRequestResult.Outcome.REQUEST_ACCEPTED,
        UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED,
        new UsernameRecoverySchemeRef("sch_opaque-1"),
        "p***@example.test",
        UsernameRecoveryDestinationType.EMAIL);
  }

  private static UsernameRecoveryRequestResult simple(UsernameRecoveryRequestResult.Outcome o) {
    return new UsernameRecoveryRequestResult(o, null, null, null, null);
  }
}

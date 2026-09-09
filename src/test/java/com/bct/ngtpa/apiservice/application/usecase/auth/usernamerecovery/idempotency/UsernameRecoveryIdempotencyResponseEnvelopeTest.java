package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class UsernameRecoveryIdempotencyResponseEnvelopeTest {
  private final UsernameRecoveryIdempotencyResponseEnvelope envelope =
      new UsernameRecoveryIdempotencyResponseEnvelope(
          new JacksonTransientJsonAdapter(new ObjectMapper()));

  @Test
  void roundTripsOnlyTheExpectedAction() {
    String body = envelope.encode(UsernameRecoveryIdempotencyAction.START, new Payload("OK"));

    assertThat(envelope.decode(UsernameRecoveryIdempotencyAction.START, body, Payload.class))
        .isEqualTo(new Payload("OK"));
    assertThatThrownBy(
            () ->
                envelope.decode(
                    UsernameRecoveryIdempotencyAction.REQUEST, body, Payload.class))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsMalformedMissingUnknownAndSensitivePayloadFields() {
    assertThatThrownBy(
            () -> envelope.decode(UsernameRecoveryIdempotencyAction.START, "{}", Payload.class))
        .isInstanceOf(IllegalArgumentException.class);

    String unknown =
        "{\"idempotencyAction\":\"start\",\"payload\":{\"status\":\"OK\",\"username\":\"secret\"}}";
    assertThatThrownBy(
            () ->
                envelope.decode(
                    UsernameRecoveryIdempotencyAction.START, unknown, Payload.class))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private record Payload(String status) {}
}

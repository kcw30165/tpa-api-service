package com.bct.ngtpa.apiservice.application.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.json.JacksonTransientJsonAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class SharedIdempotencyIntegrationPrimitivesTest {
  @Test
  void identityConstructionIsOpaqueDeterministicAndActionSeparated() {
    var factory = new IdempotencyOperationIdentityFactory();
    var capability = new IdempotencyCapability("forgot-password");
    var first =
        factory.create(capability, new IdempotencyAction("start"), "scope-secret", "abcdefgh");
    var same =
        factory.create(capability, new IdempotencyAction("start"), "scope-secret", "abcdefgh");
    var other =
        factory.create(capability, new IdempotencyAction("complete"), "scope-secret", "abcdefgh");
    assertThat(first).isEqualTo(same);
    assertThat(first.scope().value()).hasSize(64).doesNotContain("scope-secret");
    assertThat(first.action()).isNotEqualTo(other.action());
    assertThat(first.toString()).doesNotContain("scope-secret", "abcdefgh");
  }

  @Test
  void fingerprintConstructionIsVersionedLengthDelimitedAndActionSeparated() {
    var factory = new CanonicalIdempotencyFingerprintFactory();
    var capability = new IdempotencyCapability("forgot-password");
    var first = factory.create(1, capability, new IdempotencyAction("start"), List.of("ab", "c"));
    var ambiguous =
        factory.create(1, capability, new IdempotencyAction("start"), List.of("a", "bc"));
    var other =
        factory.create(1, capability, new IdempotencyAction("complete"), List.of("ab", "c"));
    assertThat(first).isNotEqualTo(ambiguous).isNotEqualTo(other);
    assertThat(first.value()).hasSize(64);
  }

  @Test
  void envelopeIsStrictAndActionTagged() {
    var envelope =
        new ActionTaggedIdempotencyResponseEnvelope(
            new JacksonTransientJsonAdapter(new ObjectMapper()));
    String encoded = envelope.encode(new IdempotencyAction("start"), new Payload("OK"));
    assertThat(envelope.decodePayload(new IdempotencyAction("start"), encoded, Payload.class))
        .isEqualTo(new Payload("OK"));
    assertThatThrownBy(
            () -> envelope.decodePayload(new IdempotencyAction("complete"), encoded, Payload.class))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void constantFailureClassifiersAreCentral() {
    assertThat(IdempotencyFailureClassifiers.provenPreDispatch().classify(new RuntimeException()))
        .isEqualTo(IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT);
    assertThat(IdempotencyFailureClassifiers.possibleSideEffect().classify(new RuntimeException()))
        .isEqualTo(IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT);
  }

  private record Payload(String status) {}
}

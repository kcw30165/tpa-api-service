package com.bct.ngtpa.apiservice.application.dto.auth.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RegistrationIdempotencyUnknownOutcomeContractTest {

  @Test
  void modelDefinesNonReplayableUnknownOutcome() {
    assertThat(RegistrationIdempotencyStatus.values())
        .contains(RegistrationIdempotencyStatus.UNKNOWN_OUTCOME);
    assertThat(RegistrationIdempotencyStartOutcome.values())
        .contains(RegistrationIdempotencyStartOutcome.UNKNOWN_OUTCOME_EXISTING);
  }

  @Test
  void commandRedactsKeyAndHash() {
    var command =
        new RegistrationIdempotencyUnknownOutcomeCommand(
            RegistrationIdempotencyAction.REGISTRATION_COMPLETE,
            new RegistrationIdempotencyKey("idem_safe"),
            new RegistrationRequestHash("a".repeat(64)),
            Instant.parse("2026-07-14T00:00:00Z"),
            Duration.ofMinutes(5));

    assertThat(command.toString()).doesNotContain("idem_safe", "a".repeat(64));
  }
}

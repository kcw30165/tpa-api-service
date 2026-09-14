package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LoginOtpVerifyRecoveryRepairSubtask006001a5rFailFirstTest {
  private static final Path MAIN = Path.of("src/main/java/com/bct/ngtpa/apiservice");

  @Test
  void otpVerifyPortExposesDurableReservationAndProofRecovery() throws Exception {
    String port =
        Files.readString(MAIN.resolve("application/port/out/LoginOtpVerifyPersistencePort.java"));
    assertThat(port)
        .contains(
            "reserveOrLoad", "loadConsumptionProof", "recordVerificationCommitted", "complete");
  }

  @Test
  void committedReplayCannotReturnNullProof() throws Exception {
    String adapter =
        Files.readString(
            MAIN.resolve(
                "adapter/out/redis/auth/login/otp/verify/RedisLoginOtpVerifyPersistenceAdapter.java"));
    assertThat(adapter).doesNotContain("ALREADY_COMMITTED, 0, null");
    assertThat(adapter).contains("loadConsumptionProof");
  }

  @Test
  void serviceUsesPublicKeyOnlyAtReservationBoundary() throws Exception {
    String service =
        Files.readString(
            MAIN.resolve("application/usecase/auth/login/otp/verify/LoginOtpVerifyService.java"));
    assertThat(service)
        .contains(
            "reserveOrLoad",
            "c.idempotencyKey()",
            "reservation.handoffReference()",
            "reservation.operationReference()");
  }
}

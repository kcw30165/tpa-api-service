package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "run.001a6.replay.fail-first", matches = "true")
class AuthenticatedLoginCommittedReplaySubtask006001a6005FailFirstTest {
  private static final Path MAIN = Path.of("src/main/java/com/bct/ngtpa/apiservice");
  private static final Path VERIFY =
      MAIN.resolve("application/usecase/auth/login/otp/verify/LoginOtpVerifyService.java");
  private static final Path REPLAY =
      MAIN.resolve("application/usecase/auth/login/replay/RecoverCommittedLoginService.java");
  private static final Path REPLAY_PORT =
      MAIN.resolve("application/port/out/AuthenticatedLoginReplaySessionPort.java");
  private static final Path HANDOFF =
      MAIN.resolve("application/dto/auth/login/handoff/AuthenticatedLoginHandoff.java");
  private static final Path ESTABLISH =
      MAIN.resolve(
          "application/usecase/auth/login/session/EstablishAuthenticatedLoginSessionService.java");

  @Test
  void committedReservationRecoversBeforePreAuthContextAndNeverVerifiesOtpAgain() throws Exception {
    String source = Files.readString(VERIFY);
    int reserve = source.indexOf("reserveOrLoad(reserve)");
    int recover = source.indexOf("recoverCommittedLogin(reservation.proof(), c)");
    int preAuth = source.indexOf("loader.load(");
    int verify = source.indexOf("otpVerifyPersistence.verify(command)");
    assertThat(reserve).isGreaterThanOrEqualTo(0);
    assertThat(recover).isGreaterThan(reserve).isLessThan(preAuth);
    assertThat(verify).isGreaterThan(preAuth);
    assertThat(source)
        .contains("if (reservation.proof() != null)")
        .contains("return recoverCommittedLogin(reservation.proof(), c)")
        .contains("case ALREADY_COMMITTED -> recoverCommittedLogin(r.proof(), c)")
        .doesNotContain(".verifyChallenge(new VerifyOtpChallengeCommand");
  }

  @Test
  void replayUsesCompletedHandoffAndAuthoritativeSessionWithoutRepeatingBusinessWork()
      throws Exception {
    String verify = Files.readString(VERIFY);
    String replay = Files.readString(REPLAY);
    assertThat(verify)
        .contains("RecoverCommittedLoginUseCase committedReplay")
        .contains("committedReplay.recover(new RecoverCommittedLoginCommand(")
        .doesNotContain("AuthenticatedSessionPort authenticatedSessions")
        .doesNotContain("ReactiveStringRedisTemplate");
    assertThat(replay)
        .contains("handoffs.loadByHandoff(proof.handoffReference())")
        .contains("AuthenticatedLoginHandoffStatus.AUTH_FLOW_DELETED")
        .contains("replaySessions.loadForReplay")
        .contains("handoff.authenticatedSessionId()")
        .contains("reconstructLoginAccounts")
        .doesNotContain("accountLookup.getAccounts")
        .doesNotContain("accountAssignment.assign")
        .doesNotContain("transition.establish")
        .doesNotContain("otpVerifyPersistence.verify")
        .doesNotContain("cleanup.delete");
  }

  @Test
  void replayRotatesOnlyTheCsrfHashConditionallyAndReleasesRawTokenAfterAcknowledgement()
      throws Exception {
    String replay = Files.readString(REPLAY);
    String port = Files.readString(REPLAY_PORT);
    assertThat(port).contains("loadForReplay").doesNotContain("rotateCsrfHashIfCurrent");
    assertThat(replay)
        .contains("csrfTokens.generate()")
        .contains("csrfHasher.hash(raw)")
        .contains("concurrency.updateIfUsable(")
        .contains("AuthenticatedSessionConcurrencyOutcome.APPLIED")
        .contains("new RecoverCommittedLoginResult(true")
        .doesNotContain("storeRawCsrf")
        .doesNotContain("encryptedCsrf");
  }

  @Test
  void firstSuccessRecordsSessionAndCleanupProofBeforeReplayCanComplete() throws Exception {
    String establish = Files.readString(ESTABLISH);
    String handoff = Files.readString(HANDOFF);
    assertThat(establish)
        .contains("recordAuthenticatedSessionCommitted")
        .contains("RecordAuthenticatedSessionCommittedCommand")
        .contains("cleanup.delete(command.authFlowSessionId())")
        .contains("recordCleanupProof")
        .contains("RecordAuthFlowCleanupProofCommand")
        .contains("thenReturn(EstablishAuthenticatedLoginSessionResult.established(");
    assertThat(handoff)
        .contains("AUTHENTICATED_SESSION_COMMITTED")
        .contains("AUTH_FLOW_DELETED")
        .contains("authenticatedSessionId")
        .contains("cleanupProof");
  }
}

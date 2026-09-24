package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSession;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.SessionRevision;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Direct coverage of {@link AuthenticatedSessionConcurrencyResult}'s compact constructor and
 * static factories. Every existing consumer (e.g. {@code
 * AuthenticatedSessionConcurrencyServiceTest}) exercises this record only indirectly through
 * {@code applied(session)} / {@code of(terminalOutcome)}, which never independently proves the
 * constructor's own two guard branches (APPLIED requiring a non-null session, and every other
 * outcome forbidding one). This class closes that gap directly.
 */
class AuthenticatedSessionConcurrencyResultTest {

  private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");

  private static AuthenticatedSession session() {
    return new AuthenticatedSession(
        new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx"),
        new ActorContext("member-1", ActorUserRole.ROLE_USER_MBR),
        List.of(),
        AuthenticatedSessionStatus.ACTIVE,
        "hash",
        NOW.minusSeconds(60),
        NOW.minusSeconds(60),
        NOW.plusSeconds(900),
        NOW.plusSeconds(28800),
        new SessionRevision("a".repeat(32)));
  }

  // ----- constructor: valid combinations -----

  @Test
  void appliedWithNonNullSessionConstructsSuccessfully() {
    var result = new AuthenticatedSessionConcurrencyResult(
        AuthenticatedSessionConcurrencyOutcome.APPLIED, session());

    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionConcurrencyOutcome.APPLIED);
    assertThat(result.session()).isNotNull();
  }

  @ParameterizedTest
  @EnumSource(
      value = AuthenticatedSessionConcurrencyOutcome.class,
      names = "APPLIED",
      mode = EnumSource.Mode.EXCLUDE)
  void everyNonAppliedOutcomeWithNullSessionConstructsSuccessfully(
      AuthenticatedSessionConcurrencyOutcome outcome) {
    var result = new AuthenticatedSessionConcurrencyResult(outcome, null);

    assertThat(result.outcome()).isEqualTo(outcome);
    assertThat(result.session()).isNull();
  }

  // ----- constructor: invalid combinations (the two guard branches) -----

  @Test
  void appliedWithNullSessionThrows() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new AuthenticatedSessionConcurrencyResult(
                    AuthenticatedSessionConcurrencyOutcome.APPLIED, null))
        .withMessageContaining("APPLIED");
  }

  @ParameterizedTest
  @EnumSource(
      value = AuthenticatedSessionConcurrencyOutcome.class,
      names = "APPLIED",
      mode = EnumSource.Mode.EXCLUDE)
  void everyNonAppliedOutcomeWithNonNullSessionThrows(
      AuthenticatedSessionConcurrencyOutcome outcome) {
    AuthenticatedSession session = session();

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new AuthenticatedSessionConcurrencyResult(outcome, session))
        .withMessageContaining("non-APPLIED");
  }

  @Test
  void nullOutcomeThrows() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new AuthenticatedSessionConcurrencyResult(null, null))
        .withMessageContaining("outcome");
  }

  // ----- static factories -----

  @Test
  void appliedFactoryProducesAnAppliedResultCarryingTheGivenSession() {
    AuthenticatedSession session = session();
    var result = AuthenticatedSessionConcurrencyResult.applied(session);

    assertThat(result.outcome()).isEqualTo(AuthenticatedSessionConcurrencyOutcome.APPLIED);
    assertThat(result.session()).isSameAs(session);
  }

  @ParameterizedTest
  @EnumSource(
      value = AuthenticatedSessionConcurrencyOutcome.class,
      names = "APPLIED",
      mode = EnumSource.Mode.EXCLUDE)
  void ofFactoryProducesATerminalResultForEveryNonAppliedOutcome(
      AuthenticatedSessionConcurrencyOutcome outcome) {
    var result = AuthenticatedSessionConcurrencyResult.of(outcome);

    assertThat(result.outcome()).isEqualTo(outcome);
    assertThat(result.session()).isNull();
  }

  @Test
  void ofFactoryRejectsTheAppliedOutcomeSinceItRequiresASession() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> AuthenticatedSessionConcurrencyResult.of(
            AuthenticatedSessionConcurrencyOutcome.APPLIED))
        .withMessageContaining("applied(session)");
  }

  // ----- confirm every enum constant is exercised (guards against a future added constant silently skipping this test) -----

  @Test
  void everyNonAppliedEnumConstantIsCoveredByTheParameterizedTests() {
    long nonAppliedCount =
        Stream.of(AuthenticatedSessionConcurrencyOutcome.values())
            .filter(o -> o != AuthenticatedSessionConcurrencyOutcome.APPLIED)
            .count();

    assertThat(nonAppliedCount).isEqualTo(4);
  }
}

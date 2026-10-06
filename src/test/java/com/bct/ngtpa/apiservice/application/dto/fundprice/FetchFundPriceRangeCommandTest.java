package com.bct.ngtpa.apiservice.application.dto.fundprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Fail-first: {@code FetchFundPriceRangeCommand} does not exist yet. Carries an explicit
 * fromDate/toDate range with NO mode field at all -- confirmed design (Nick): export has no mode
 * concept; it passes policyNo, fromDate, toDate straight to APIM, bypassing
 * {@link FetchFundPriceCommand}'s single-date + mode-dependent expansion entirely.
 */
class FetchFundPriceRangeCommandTest {

  private static final LocalDate FROM = LocalDate.of(2023, 9, 25);
  private static final LocalDate TO = LocalDate.of(2026, 9, 25);

  private static FetchFundPriceRangeCommand valid() {
    return new FetchFundPriceRangeCommand(
        "00000008802", FROM, TO, "DB", ActorUserRole.ROLE_USER_MBR);
  }

  @Test
  void constructsWithAllValidFields() {
    var command = valid();
    assertThat(command.policyNo()).isEqualTo("00000008802");
    assertThat(command.fromDate()).isEqualTo(FROM);
    assertThat(command.toDate()).isEqualTo(TO);
    assertThat(command.accountEnv()).isEqualTo("DB");
    assertThat(command.actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
  }

  @Test
  void nullPolicyNoThrows() {
    assertThatThrownBy(
            () ->
                new FetchFundPriceRangeCommand(null, FROM, TO, "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyNo");
  }

  @Test
  void blankPolicyNoThrows() {
    assertThatThrownBy(
            () -> new FetchFundPriceRangeCommand(" ", FROM, TO, "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyNo");
  }

  @Test
  void nullFromDateThrows() {
    assertThatThrownBy(
            () ->
                new FetchFundPriceRangeCommand(
                    "00000008802", null, TO, "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("fromDate");
  }

  @Test
  void nullToDateThrows() {
    assertThatThrownBy(
            () ->
                new FetchFundPriceRangeCommand(
                    "00000008802", FROM, null, "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("toDate");
  }

  @Test
  void fromDateAfterToDateThrows() {
    assertThatThrownBy(
            () ->
                new FetchFundPriceRangeCommand(
                    "00000008802", TO, FROM, "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullAccountEnvThrows() {
    assertThatThrownBy(
            () ->
                new FetchFundPriceRangeCommand(
                    "00000008802", FROM, TO, null, ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountEnv");
  }

  @Test
  void blankAccountEnvThrows() {
    // Confirmed real gap (script 159/160): only accountEnv==null was previously exercised;
    // accountEnv.isBlank()==true was never independently tested, unlike policyNo's own
    // blank-vs-null distinction (blankPolicyNoThrows already exists above).
    assertThatThrownBy(
            () ->
                new FetchFundPriceRangeCommand(
                    "00000008802", FROM, TO, " ", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountEnv");
  }

  @Test
  void nullActorUserRoleThrows() {
    assertThatThrownBy(
            () -> new FetchFundPriceRangeCommand("00000008802", FROM, TO, "DB", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("actorUserRole");
  }
}

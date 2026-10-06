package com.bct.ngtpa.apiservice.application.dto.fundprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Fail-first: {@code FetchFundPriceCommand} does not exist yet.
 *
 * <p>Carries ONE validated FE date, unmanipulated -- Java does not expand it into a
 * from-date/to-date range at this layer. {@code mode} rides alongside so the adapter (not this
 * command, not the use case) decides how to expand the date when building the downstream
 * request. Confirmed design decision, not an assumption.
 */
class FetchFundPriceCommandTest {

  private static final LocalDate DATE = LocalDate.of(2026, 5, 5);

  private static FetchFundPriceCommand valid() {
    return new FetchFundPriceCommand(
        "00000008802", DATE, "DAILY", "DB", ActorUserRole.ROLE_USER_MBR);
  }

  @Test
  void constructsWithAllValidFieldsAndCarriesTheDateUnmanipulated() {
    var command = valid();
    assertThat(command.policyNo()).isEqualTo("00000008802");
    assertThat(command.date()).isEqualTo(DATE);
    assertThat(command.mode()).isEqualTo("DAILY");
    assertThat(command.accountEnv()).isEqualTo("DB");
    assertThat(command.actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
  }

  @Test
  void nullPolicyNoThrows() {
    assertThatThrownBy(
            () -> new FetchFundPriceCommand(null, DATE, "DAILY", "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyNo");
  }

  @Test
  void blankPolicyNoThrows() {
    assertThatThrownBy(
            () -> new FetchFundPriceCommand(" ", DATE, "DAILY", "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyNo");
  }

  @Test
  void nullDateThrows() {
    assertThatThrownBy(
            () ->
                new FetchFundPriceCommand(
                    "00000008802", null, "DAILY", "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("date");
  }

  @Test
  void nullModeThrows() {
    assertThatThrownBy(
            () ->
                new FetchFundPriceCommand(
                    "00000008802", DATE, null, "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mode");
  }

  @Test
  void blankModeThrows() {
    assertThatThrownBy(
            () ->
                new FetchFundPriceCommand(
                    "00000008802", DATE, "  ", "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mode");
  }

  @Test
  void nullAccountEnvThrows() {
    assertThatThrownBy(
            () ->
                new FetchFundPriceCommand(
                    "00000008802", DATE, "DAILY", null, ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountEnv");
  }

  @Test
  void blankAccountEnvThrows() {
    // Confirmed real gap (script 159/160): only accountEnv==null was previously exercised;
    // accountEnv.isBlank()==true was never independently tested, unlike every other field's
    // blank-vs-null distinction (policyNo, mode already have both).
    assertThatThrownBy(
            () ->
                new FetchFundPriceCommand(
                    "00000008802", DATE, "DAILY", " ", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountEnv");
  }

  @Test
  void nullActorUserRoleThrows() {
    assertThatThrownBy(
            () -> new FetchFundPriceCommand("00000008802", DATE, "DAILY", "DB", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("actorUserRole");
  }

  @Test
  void monthlyModeAlsoAcceptedWithTheSameUnmanipulatedDate() {
    // Confirms: MONTHLY does NOT change what this command carries -- expansion into
    // first/last day of month is the adapter's job, not this command's.
    var command =
        new FetchFundPriceCommand(
            "00000008802", DATE, "MONTHLY", "DB", ActorUserRole.ROLE_USER_MBR);
    assertThat(command.date()).isEqualTo(DATE);
    assertThat(command.mode()).isEqualTo("MONTHLY");
  }
}

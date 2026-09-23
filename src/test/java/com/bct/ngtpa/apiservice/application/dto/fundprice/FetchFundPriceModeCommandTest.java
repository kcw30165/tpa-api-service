package com.bct.ngtpa.apiservice.application.dto.fundprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Closes the coverage gap identified by script 030's JaCoCo XML parse: this record's compact
 * constructor previously had no dedicated test, so both blank-guard branches were only ever
 * exercised on their non-throwing (valid-input) side via
 * {@code ApimFundPriceModeSourceAdapterTest}. Targets 95% instruction / 95% branch (DTO tier).
 */
class FetchFundPriceModeCommandTest {

  private static final LocalDate REF_DATE = LocalDate.of(2026, 9, 18);

  private static FetchFundPriceModeCommand valid() {
    return new FetchFundPriceModeCommand("00000008802", REF_DATE, "DB", ActorUserRole.ROLE_USER_MBR);
  }

  @Test
  void constructsWithAllValidFields() {
    var command = valid();
    assertThat(command.policyNo()).isEqualTo("00000008802");
    assertThat(command.referenceDate()).isEqualTo(REF_DATE);
    assertThat(command.accountEnv()).isEqualTo("DB");
    assertThat(command.actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
  }

  @Test
  void nullPolicyNoThrows() {
    assertThatThrownBy(
            () -> new FetchFundPriceModeCommand(null, REF_DATE, "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyNo");
  }

  @Test
  void blankPolicyNoThrows() {
    assertThatThrownBy(
            () -> new FetchFundPriceModeCommand("   ", REF_DATE, "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyNo");
  }

  @Test
  void nullReferenceDateThrows() {
    assertThatThrownBy(
            () ->
                new FetchFundPriceModeCommand(
                    "00000008802", null, "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("referenceDate");
  }

  @Test
  void nullAccountEnvThrows() {
    assertThatThrownBy(
            () ->
                new FetchFundPriceModeCommand(
                    "00000008802", REF_DATE, null, ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountEnv");
  }

  @Test
  void blankAccountEnvThrows() {
    assertThatThrownBy(
            () ->
                new FetchFundPriceModeCommand("00000008802", REF_DATE, "  ", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountEnv");
  }

  @Test
  void nullActorUserRoleThrows() {
    assertThatThrownBy(() -> new FetchFundPriceModeCommand("00000008802", REF_DATE, "DB", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("actorUserRole");
  }
}

package com.bct.ngtpa.apiservice.application.dto.fundfactsheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FetchFundFactSheetCommandTest {

  private static final LocalDate DATE = LocalDate.of(2026, 5, 5);

  @Test
  void carriesAllFourFieldsUnchanged() {
    var command = new FetchFundFactSheetCommand("00000008802", DATE, "JP", ActorUserRole.ROLE_USER_MBR);

    assertThat(command.policyNo()).isEqualTo("00000008802");
    assertThat(command.referenceDate()).isEqualTo(DATE);
    assertThat(command.accountEnv()).isEqualTo("JP");
    assertThat(command.actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
  }

  @Test
  void rejectsNullAndBlankPolicyNo() {
    assertThatThrownBy(() -> new FetchFundFactSheetCommand(null, DATE, "JP", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyNo");
    assertThatThrownBy(() -> new FetchFundFactSheetCommand("  ", DATE, "JP", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyNo");
  }

  @Test
  void rejectsNullReferenceDate() {
    assertThatThrownBy(
            () -> new FetchFundFactSheetCommand("00000008802", null, "JP", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("referenceDate");
  }

  @Test
  void rejectsNullAndBlankAccountEnv() {
    assertThatThrownBy(
            () -> new FetchFundFactSheetCommand("00000008802", DATE, null, ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountEnv");
    assertThatThrownBy(
            () -> new FetchFundFactSheetCommand("00000008802", DATE, " ", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountEnv");
  }

  @Test
  void rejectsNullActorUserRole() {
    assertThatThrownBy(() -> new FetchFundFactSheetCommand("00000008802", DATE, "JP", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("actorUserRole");
  }

  @Test
  void rejectionMessagesAreFixedTextAndNeverCarryTheOffendingValue() {
    assertThatThrownBy(
            () -> new FetchFundFactSheetCommand("   ", DATE, "JP", ActorUserRole.ROLE_USER_MBR))
        .hasMessage("policyNo must not be blank");
    assertThatThrownBy(
            () -> new FetchFundFactSheetCommand("00000008802", DATE, "  ", ActorUserRole.ROLE_USER_MBR))
        .hasMessage("accountEnv must not be blank");
  }
}

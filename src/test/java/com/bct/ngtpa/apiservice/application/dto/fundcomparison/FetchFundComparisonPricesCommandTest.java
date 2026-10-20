package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Closes-by-construction: FetchFundComparisonPricesCommand's compact constructor, per
 * docs/design/13-fund-comparison/07-implementation-plan.md section 7.4 sub-task 3.1 (corrected
 * design). Mirrors FetchFundPriceRangeCommand's exact guard-clause pattern (confirmed verbatim,
 * script 053 evidence) plus a fundList guard, since this command is the ONLY one of the three
 * TRPGetFundPrice commands that carries a real, non-empty fund-list value.
 */
class FetchFundComparisonPricesCommandTest {

  private static FetchFundComparisonPricesCommand validCommand(
      String policyNo,
      LocalDate fromDate,
      LocalDate toDate,
      String fundList,
      String accountEnv,
      ActorUserRole role) {
    return new FetchFundComparisonPricesCommand(
        policyNo, fromDate, toDate, fundList, accountEnv, role);
  }

  @Test
  void acceptsValidArguments() {
    var command =
        validCommand(
            "00000008802",
            LocalDate.of(2026, 10, 6),
            LocalDate.of(2026, 10, 15),
            "FAEU,FBAU",
            "DB",
            ActorUserRole.ROLE_USER_MBR);

    assertThat(command.policyNo()).isEqualTo("00000008802");
    assertThat(command.fromDate()).isEqualTo(LocalDate.of(2026, 10, 6));
    assertThat(command.toDate()).isEqualTo(LocalDate.of(2026, 10, 15));
    assertThat(command.fundList()).isEqualTo("FAEU,FBAU");
    assertThat(command.accountEnv()).isEqualTo("DB");
    assertThat(command.actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
  }

  @Test
  void rejectsNullPolicyNo() {
    var fromDate = LocalDate.of(2026, 10, 6);
    var toDate = LocalDate.of(2026, 10, 15);
    assertThatThrownBy(
            () -> validCommand(null, fromDate, toDate, "FAEU", "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyNo");
  }

  @Test
  void rejectsBlankPolicyNo() {
    var fromDate = LocalDate.of(2026, 10, 6);
    var toDate = LocalDate.of(2026, 10, 15);
    assertThatThrownBy(
            () -> validCommand("   ", fromDate, toDate, "FAEU", "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyNo");
  }

  @Test
  void rejectsNullFromDate() {
    var toDate = LocalDate.of(2026, 10, 15);
    assertThatThrownBy(
            () ->
                validCommand(
                    "00000008802", null, toDate, "FAEU", "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("fromDate");
  }

  @Test
  void rejectsNullToDate() {
    var fromDate = LocalDate.of(2026, 10, 6);
    assertThatThrownBy(
            () ->
                validCommand(
                    "00000008802", fromDate, null, "FAEU", "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("toDate");
  }

  @Test
  void rejectsFromDateAfterToDate() {
    var fromDate = LocalDate.of(2026, 10, 16);
    var toDate = LocalDate.of(2026, 10, 15);
    assertThatThrownBy(
            () ->
                validCommand(
                    "00000008802", fromDate, toDate, "FAEU", "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsFromDateEqualToToDate() {
    var sameDate = LocalDate.of(2026, 10, 15);
    var command =
        validCommand("00000008802", sameDate, sameDate, "FAEU", "DB", ActorUserRole.ROLE_USER_MBR);
    assertThat(command.fromDate()).isEqualTo(sameDate);
    assertThat(command.toDate()).isEqualTo(sameDate);
  }

  @Test
  void rejectsNullFundList() {
    var fromDate = LocalDate.of(2026, 10, 6);
    var toDate = LocalDate.of(2026, 10, 15);
    assertThatThrownBy(
            () ->
                validCommand(
                    "00000008802", fromDate, toDate, null, "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fundList");
  }

  @Test
  void rejectsBlankFundList() {
    var fromDate = LocalDate.of(2026, 10, 6);
    var toDate = LocalDate.of(2026, 10, 15);
    assertThatThrownBy(
            () ->
                validCommand(
                    "00000008802", fromDate, toDate, "   ", "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fundList");
  }

  @Test
  void rejectsNullAccountEnv() {
    var fromDate = LocalDate.of(2026, 10, 6);
    var toDate = LocalDate.of(2026, 10, 15);
    assertThatThrownBy(
            () ->
                validCommand(
                    "00000008802", fromDate, toDate, "FAEU", null, ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountEnv");
  }

  @Test
  void rejectsBlankAccountEnv() {
    var fromDate = LocalDate.of(2026, 10, 6);
    var toDate = LocalDate.of(2026, 10, 15);
    assertThatThrownBy(
            () ->
                validCommand(
                    "00000008802", fromDate, toDate, "FAEU", "   ", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountEnv");
  }

  @Test
  void rejectsNullActorUserRole() {
    var fromDate = LocalDate.of(2026, 10, 6);
    var toDate = LocalDate.of(2026, 10, 15);
    assertThatThrownBy(() -> validCommand("00000008802", fromDate, toDate, "FAEU", "DB", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("actorUserRole");
  }
}

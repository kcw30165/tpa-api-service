package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fail-first test for the Milestone 3 prices use-case input command (sub-task 3.3), per
 * docs/design/13-fund-comparison/07-implementation-plan.md section 7.4. A flat record (mirroring
 * {@code FetchFundComparisonOptionsCommand}'s own style), named with the "UseCaseCommand" suffix
 * specifically to avoid a same-package class-name collision with the PORT-level {@code
 * FetchFundComparisonPricesCommand} (sub-task 3.1), which carries genuinely different data
 * (already-computed fromDate/toDate, a pre-joined fund-list string).
 */
class FetchFundComparisonPricesUseCaseCommandTest {

  private static List<String> codes(int count) {
    List<String> result = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      result.add("F" + i);
    }
    return result;
  }

  @Test
  void acceptsValidArgumentsAndCarriesFieldsVerbatim() {
    var command =
        new FetchFundComparisonPricesUseCaseCommand(
            "00000008802", "DB", ActorUserRole.ROLE_USER_MBR, List.of("FAEU", "FBAU"), "10D");
    assertThat(command.policyNo()).isEqualTo("00000008802");
    assertThat(command.accountEnv()).isEqualTo("DB");
    assertThat(command.actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
    assertThat(command.fundCodes()).containsExactly("FAEU", "FBAU");
    assertThat(command.timeRange()).isEqualTo("10D");
  }

  @Test
  void rejectsNullPolicyNo() {
    var fundCodes = List.of("FAEU");
    assertThatThrownBy(
            () ->
                new FetchFundComparisonPricesUseCaseCommand(
                    null, "DB", ActorUserRole.ROLE_USER_MBR, fundCodes, "10D"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyNo");
  }

  @Test
  void rejectsBlankPolicyNo() {
    var fundCodes = List.of("FAEU");
    assertThatThrownBy(
            () ->
                new FetchFundComparisonPricesUseCaseCommand(
                    "  ", "DB", ActorUserRole.ROLE_USER_MBR, fundCodes, "10D"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyNo");
  }

  @Test
  void rejectsNullAccountEnv() {
    var fundCodes = List.of("FAEU");
    assertThatThrownBy(
            () ->
                new FetchFundComparisonPricesUseCaseCommand(
                    "00000008802", null, ActorUserRole.ROLE_USER_MBR, fundCodes, "10D"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountEnv");
  }

  @Test
  void rejectsBlankAccountEnv() {
    var fundCodes = List.of("FAEU");
    assertThatThrownBy(
            () ->
                new FetchFundComparisonPricesUseCaseCommand(
                    "00000008802", "  ", ActorUserRole.ROLE_USER_MBR, fundCodes, "10D"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountEnv");
  }

  @Test
  void rejectsNullActorUserRole() {
    var fundCodes = List.of("FAEU");
    assertThatThrownBy(
            () ->
                new FetchFundComparisonPricesUseCaseCommand(
                    "00000008802", "DB", null, fundCodes, "10D"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("actorUserRole");
  }

  @Test
  void rejectsNullFundCodes() {
    assertThatThrownBy(
            () ->
                new FetchFundComparisonPricesUseCaseCommand(
                    "00000008802", "DB", ActorUserRole.ROLE_USER_MBR, null, "10D"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("fundCodes");
  }

  @Test
  void rejectsEmptyFundCodes() {
    var fundCodes = List.<String>of();
    assertThatThrownBy(
            () ->
                new FetchFundComparisonPricesUseCaseCommand(
                    "00000008802", "DB", ActorUserRole.ROLE_USER_MBR, fundCodes, "10D"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fundCodes");
  }

  @Test
  void rejectsMoreThanFiveFundCodes() {
    var fundCodes = codes(6);
    assertThatThrownBy(
            () ->
                new FetchFundComparisonPricesUseCaseCommand(
                    "00000008802", "DB", ActorUserRole.ROLE_USER_MBR, fundCodes, "10D"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("5");
  }

  @Test
  void acceptsExactlyFiveFundCodes() {
    var command =
        new FetchFundComparisonPricesUseCaseCommand(
            "00000008802", "DB", ActorUserRole.ROLE_USER_MBR, codes(5), "10D");
    assertThat(command.fundCodes()).hasSize(5);
  }

  @Test
  void rejectsNullTimeRange() {
    var fundCodes = List.of("FAEU");
    assertThatThrownBy(
            () ->
                new FetchFundComparisonPricesUseCaseCommand(
                    "00000008802", "DB", ActorUserRole.ROLE_USER_MBR, fundCodes, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timeRange");
  }

  @Test
  void rejectsBlankTimeRange() {
    var fundCodes = List.of("FAEU");
    assertThatThrownBy(
            () ->
                new FetchFundComparisonPricesUseCaseCommand(
                    "00000008802", "DB", ActorUserRole.ROLE_USER_MBR, fundCodes, " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timeRange");
  }

  @Test
  void fundCodesListIsImmutable() {
    var mutable = new ArrayList<String>();
    mutable.add("FAEU");
    var command =
        new FetchFundComparisonPricesUseCaseCommand(
            "00000008802", "DB", ActorUserRole.ROLE_USER_MBR, mutable, "10D");
    mutable.add("FBAU");
    assertThat(command.fundCodes()).containsExactly("FAEU");
  }
}

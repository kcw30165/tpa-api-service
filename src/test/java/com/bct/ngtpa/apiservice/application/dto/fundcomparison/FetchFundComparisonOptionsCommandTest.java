package com.bct.ngtpa.apiservice.application.dto.fundcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import org.junit.jupiter.api.Test;

/**
 * Closes the confirmed jacoco gap on FetchFundComparisonOptionsCommand's compact constructor
 * (script 049 evidence, lines 17-18, 20-21): no existing test file constructs this record directly
 * with blank/null policyNo or accountEnv, so both throw-paths were never exercised (every real
 * caller, the controller, always passes non-blank trusted values from PortalAccessContext).
 */
class FetchFundComparisonOptionsCommandTest {

  @Test
  void rejectsNullPolicyNo() {
    assertThatThrownBy(
            () -> new FetchFundComparisonOptionsCommand(null, "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyNo");
  }

  @Test
  void rejectsBlankPolicyNo() {
    assertThatThrownBy(
            () -> new FetchFundComparisonOptionsCommand("   ", "DB", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("policyNo");
  }

  @Test
  void rejectsNullAccountEnv() {
    assertThatThrownBy(
            () ->
                new FetchFundComparisonOptionsCommand(
                    "00000008802", null, ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountEnv");
  }

  @Test
  void rejectsBlankAccountEnv() {
    assertThatThrownBy(
            () ->
                new FetchFundComparisonOptionsCommand(
                    "00000008802", "   ", ActorUserRole.ROLE_USER_MBR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accountEnv");
  }

  @Test
  void rejectsNullActorUserRole() {
    assertThatThrownBy(() -> new FetchFundComparisonOptionsCommand("00000008802", "DB", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("actorUserRole");
  }

  @Test
  void acceptsValidArguments() {
    var command =
        new FetchFundComparisonOptionsCommand("00000008802", "DB", ActorUserRole.ROLE_USER_MBR);
    assertThat(command.policyNo()).isEqualTo("00000008802");
    assertThat(command.accountEnv()).isEqualTo("DB");
    assertThat(command.actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
  }
}

package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InvestmentChoiceDetailsCommandTest {

  @Test
  void rejectsNullActorUserRole() {
    assertThatThrownBy(() -> command(null, "DB", "policy", "cert", LocalDate.of(2026, 11, 5)))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("actorUserRole");
  }

  @Test
  void rejectsNullAccountEnv() {
    assertThatThrownBy(
            () ->
                command(
                    ActorUserRole.ROLE_USER_MBR, null, "policy", "cert", LocalDate.of(2026, 11, 5)))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("accountEnv");
  }

  @Test
  void rejectsNullPolicyNo() {
    assertThatThrownBy(
            () ->
                command(ActorUserRole.ROLE_USER_MBR, "DB", null, "cert", LocalDate.of(2026, 11, 5)))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("policyNo");
  }

  @Test
  void rejectsNullCertNo() {
    assertThatThrownBy(
            () ->
                command(
                    ActorUserRole.ROLE_USER_MBR, "DB", "policy", null, LocalDate.of(2026, 11, 5)))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("certNo");
  }

  @Test
  void rejectsNullReferenceDate() {
    assertThatThrownBy(() -> command(ActorUserRole.ROLE_USER_MBR, "DB", "policy", "cert", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("refDate");
  }

  private static InvestmentChoiceDetailsCommand command(
      ActorUserRole actorUserRole,
      String accountEnv,
      String policyNo,
      String certNo,
      LocalDate date) {
    return new InvestmentChoiceDetailsCommand(actorUserRole, accountEnv, policyNo, certNo, date);
  }
}

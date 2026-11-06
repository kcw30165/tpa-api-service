package com.bct.ngtpa.apiservice.application.dto.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvestmentChoiceInstructionCommandTest {

  @Test
  void rejectsNullCommandComponents() {
    var row = new InvestmentChoiceInstructionRow("BO", "FBAL", 60);
    var rows = List.of(row);
    assertThatThrownBy(
            () ->
                new InvestmentChoiceInstructionCommand(
                    null, ActorUserRole.ROLE_INPUT, "JP", "policy", "cert", rows))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new InvestmentChoiceInstructionCommand("user", null, "JP", "policy", "cert", rows))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new InvestmentChoiceInstructionCommand(
                    "user", ActorUserRole.ROLE_INPUT, null, "policy", "cert", rows))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new InvestmentChoiceInstructionCommand(
                    "user", ActorUserRole.ROLE_INPUT, "JP", null, "cert", rows))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new InvestmentChoiceInstructionCommand(
                    "user", ActorUserRole.ROLE_INPUT, "JP", "policy", null, rows))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                new InvestmentChoiceInstructionCommand(
                    "user", ActorUserRole.ROLE_INPUT, "JP", "policy", "cert", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejectsEmptyRowsAndNullRows() {
    var emptyRows = List.<InvestmentChoiceInstructionRow>of();
    var nullRows = java.util.Collections.<InvestmentChoiceInstructionRow>singletonList(null);
    assertThatThrownBy(
            () ->
                new InvestmentChoiceInstructionCommand(
                    "user", ActorUserRole.ROLE_INPUT, "JP", "policy", "cert", emptyRows))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new InvestmentChoiceInstructionCommand(
                    "user", ActorUserRole.ROLE_INPUT, "JP", "policy", "cert", nullRows))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rowRequiresBothStringComponents() {
    assertThatThrownBy(() -> new InvestmentChoiceInstructionRow(null, "FBAL", 60))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new InvestmentChoiceInstructionRow("BO", null, 60))
        .isInstanceOf(NullPointerException.class);
    assertThat(new InvestmentChoiceInstructionRow("BO", "FBAL", 60).percentage()).isEqualTo(60);
  }
}

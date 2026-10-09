package com.bct.ngtpa.apiservice.application.dto.fundfactsheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class FetchFundFactSheetCommandTest {

  private static final String POLICY_NO = "00000008802";
  private static final ActorUserRole ROLE = ActorUserRole.ROLE_USER_MBR;
  private static final List<String> ABSENT = Arrays.asList(null, "", "   ");

  @Test
  void carriesAllFiveFieldsUnchanged() {
    var command = new FetchFundFactSheetCommand(POLICY_NO, "230", "OE", "JP", ROLE);

    assertThat(command.policyNo()).isEqualTo(POLICY_NO);
    assertThat(command.certNo()).isEqualTo("230");
    assertThat(command.schemeType()).isEqualTo("OE");
    assertThat(command.accountEnv()).isEqualTo("JP");
    assertThat(command.actorUserRole()).isEqualTo(ROLE);
    assertThat(FetchFundFactSheetCommand.class.getRecordComponents()).hasSize(5);
  }

  @Test
  void rejectsNullAndBlankPolicyNo() {
    for (String absent : ABSENT) {
      assertThatThrownBy(() -> new FetchFundFactSheetCommand(absent, "230", "OE", "JP", ROLE))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("policyNo");
    }
  }

  @Test
  void rejectsNullAndBlankCertNo() {
    for (String absent : ABSENT) {
      assertThatThrownBy(() -> new FetchFundFactSheetCommand(POLICY_NO, absent, "OE", "JP", ROLE))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("certNo");
    }
  }

  @Test
  void rejectsNullAndBlankSchemeType() {
    for (String absent : ABSENT) {
      assertThatThrownBy(() -> new FetchFundFactSheetCommand(POLICY_NO, "230", absent, "JP", ROLE))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("schemeType");
    }
  }

  @Test
  void rejectsNullAndBlankAccountEnv() {
    for (String absent : ABSENT) {
      assertThatThrownBy(() -> new FetchFundFactSheetCommand(POLICY_NO, "230", "OE", absent, ROLE))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("accountEnv");
    }
  }

  @Test
  void rejectsNullActorUserRole() {
    assertThatThrownBy(() -> new FetchFundFactSheetCommand(POLICY_NO, "230", "OE", "JP", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("actorUserRole");
  }

  @Test
  void rejectionMessagesAreFixedTextAndNeverCarryTheOffendingValue() {
    assertThatThrownBy(() -> new FetchFundFactSheetCommand("   ", "230", "OE", "JP", ROLE))
        .hasMessage("policyNo must not be blank");
    assertThatThrownBy(() -> new FetchFundFactSheetCommand(POLICY_NO, "  ", "OE", "JP", ROLE))
        .hasMessage("certNo must not be blank");
    assertThatThrownBy(() -> new FetchFundFactSheetCommand(POLICY_NO, "230", " ", "JP", ROLE))
        .hasMessage("schemeType must not be blank");
    assertThatThrownBy(() -> new FetchFundFactSheetCommand(POLICY_NO, "230", "OE", "  ", ROLE))
        .hasMessage("accountEnv must not be blank");
  }

  @Test
  void certNoAndSchemeTypeAreNotTrimmedOrConverted() {
    var command = new FetchFundFactSheetCommand(POLICY_NO, " 0230 ", "oe", "JP", ROLE);

    assertThat(command.certNo()).isEqualTo(" 0230 ");
    assertThat(command.schemeType()).isEqualTo("oe");
  }
}

package com.bct.ngtpa.apiservice.application.dto.fundfactsheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FundFactSheetResultTest {

  private static FundFactSheetRowResult row(int seq) {
    return new FundFactSheetRowResult(seq, "Fund " + seq, null, null, "https://example.test/f.pdf");
  }

  @Test
  void rowCarriesEveryFieldAsGiven() {
    var row = new FundFactSheetRowResult(7, null, "", null, "x");

    assertThat(row.seq()).isEqualTo(7);
    assertThat(row.nameEng()).isNull();
    assertThat(row.nameChi()).isEqualTo("");
    assertThat(row.linkEng()).isNull();
    assertThat(row.linkChi()).isEqualTo("x");
  }

  @Test
  void resultKeepsRowsInOrder() {
    var result = new FundFactSheetResult(List.of(row(3), row(1), row(2)));

    assertThat(result.rows()).extracting(FundFactSheetRowResult::seq).containsExactly(3, 1, 2);
  }

  @Test
  void nullRowsBecomeAnEmptyList() {
    assertThat(new FundFactSheetResult(null).rows()).isEmpty();
  }

  @Test
  void resultIsADefensiveCopyAndIsUnmodifiable() {
    List<FundFactSheetRowResult> source = new ArrayList<>(List.of(row(1)));
    var result = new FundFactSheetResult(source);

    source.add(row(2));

    assertThat(result.rows()).hasSize(1);
    assertThatThrownBy(() -> result.rows().add(row(3)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void commandCarriesTheThreeTrustedValuesAndNoLanguage() {
    var command = new GetFundFactSheetCommand("JP", "00000008802", ActorUserRole.ROLE_USER_MBR);

    assertThat(command.accountEnv()).isEqualTo("JP");
    assertThat(command.policyNo()).isEqualTo("00000008802");
    assertThat(command.actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
    assertThat(GetFundFactSheetCommand.class.getRecordComponents()).hasSize(3);
  }
}

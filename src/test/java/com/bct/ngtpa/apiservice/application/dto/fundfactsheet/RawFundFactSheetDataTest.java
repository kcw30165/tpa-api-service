package com.bct.ngtpa.apiservice.application.dto.fundfactsheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RawFundFactSheetDataTest {

  private static RawFundFactSheetRow row(Integer seq) {
    return new RawFundFactSheetRow(seq, "Fund " + seq, "", null, "https://example.test/f.pdf");
  }

  @Test
  void rowKeepsEveryFieldIncludingNullSeqAndEmptyStrings() {
    var row = new RawFundFactSheetRow(null, null, "", "", null);

    assertThat(row.seq()).isNull();
    assertThat(row.nameEng()).isNull();
    assertThat(row.nameChi()).isEqualTo("");
    assertThat(row.linkEng()).isEqualTo("");
    assertThat(row.linkChi()).isNull();
  }

  @Test
  void dataKeepsRowsInOrder() {
    var data = new RawFundFactSheetData(List.of(row(3), row(1), row(2)));

    assertThat(data.rows()).extracting(RawFundFactSheetRow::seq).containsExactly(3, 1, 2);
  }

  @Test
  void nullRowsBecomeAnEmptyList() {
    assertThat(new RawFundFactSheetData(null).rows()).isEmpty();
  }

  @Test
  void dataIsADefensiveCopy() {
    List<RawFundFactSheetRow> source = new ArrayList<>(List.of(row(1)));
    var data = new RawFundFactSheetData(source);

    source.add(row(2));

    assertThat(data.rows()).hasSize(1);
    assertThatThrownBy(() -> data.rows().add(row(3)))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}

package com.bct.ngtpa.apiservice.adapter.in.web.fundfactsheet.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.fundfactsheet.response.FundFactSheetRowResponse;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FundFactSheetResult;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FundFactSheetRowResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class FundFactSheetWebMapperTest {

  private static final String EN_LINK = "https://example.test/en.pdf";
  private static final String ZH_LINK = "https://example.test/zh.pdf";
  private static final String ZH_A = "\u7532\u57fa\u91d1";
  private static final String ZH_B = "\u4e59\u57fa\u91d1";

  private final FundFactSheetWebMapper mapper = new FundFactSheetWebMapper();

  private static FundFactSheetRowResult row(
      int seq, String nameEng, String nameChi, String linkEng, String linkChi) {
    return new FundFactSheetRowResult(seq, nameEng, nameChi, linkEng, linkChi);
  }

  private List<FundFactSheetRowResponse> map(String language, FundFactSheetRowResult... rows) {
    return mapper.toResultResponse(new FundFactSheetResult(List.of(rows)), language).data().rows();
  }

  private List<Integer> seqs(String language, FundFactSheetRowResult... rows) {
    return map(language, rows).stream().map(FundFactSheetRowResponse::seq).toList();
  }

  @Test
  void englishSelectsTheEnglishNameAndLink() {
    var rows = map("en", row(1, "Fund A", ZH_A, EN_LINK, ZH_LINK));

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).seq()).isEqualTo(1);
    assertThat(rows.get(0).fundName()).isEqualTo("Fund A");
    assertThat(rows.get(0).factsheetUrl()).isEqualTo(EN_LINK);
  }

  @Test
  void zhHkSelectsTheChineseNameAndLink() {
    var rows = map("zh_HK", row(1, "Fund A", ZH_A, EN_LINK, ZH_LINK));

    assertThat(rows.get(0).fundName()).isEqualTo(ZH_A);
    assertThat(rows.get(0).factsheetUrl()).isEqualTo(ZH_LINK);
  }

  @Test
  void zhHkFallsBackToTheEnglishNameWhenTheChineseNameIsNullBlankOrWhitespace() {
    var rows =
        map(
            "zh_HK",
            row(1, "Fund A", null, null, null),
            row(2, "Fund B", "", null, null),
            row(3, "Fund C", "   ", null, null));

    assertThat(rows)
        .extracting(FundFactSheetRowResponse::fundName)
        .containsExactly("Fund A", "Fund B", "Fund C");
  }

  @Test
  void zhHkNeverFallsBackToTheEnglishLink() {
    var rows = map("zh_HK", row(1, "Fund A", ZH_A, EN_LINK, null));

    assertThat(rows.get(0).factsheetUrl()).isNull();
  }

  @Test
  void englishNeverFallsBackToTheChineseLink() {
    var rows = map("en", row(1, "Fund A", ZH_A, null, ZH_LINK));

    assertThat(rows.get(0).factsheetUrl()).isNull();
  }

  @Test
  void bothBilingualNamesArePassedThroughAsReceivedForEveryLanguage() {
    for (String language : List.of("en", "zh_HK")) {
      var rows =
          map(
              language,
              row(1, "Fund A", null, null, null),
              row(2, "Fund B", "", null, null),
              row(3, "Fund C ", "zh ", null, null));

      assertThat(rows)
          .extracting(FundFactSheetRowResponse::fundNameEn)
          .containsExactly("Fund A", "Fund B", "Fund C ");
      assertThat(rows)
          .extracting(FundFactSheetRowResponse::fundNameZh)
          .containsExactly(null, "", "zh ");
    }
  }

  @Test
  void chineseLanguageVariantsAreNormalizedToTheChineseSide() {
    for (String language : List.of("zh_HK", "zh-HK", "zh", "ZH-hk")) {
      var rows = map(language, row(1, "Fund A", ZH_A, EN_LINK, ZH_LINK));

      assertThat(rows.get(0).fundName()).as(language).isEqualTo(ZH_A);
      assertThat(rows.get(0).factsheetUrl()).as(language).isEqualTo(ZH_LINK);
    }
  }

  @Test
  void everyOtherLanguageSelectsTheEnglishSide() {
    for (String language : java.util.Arrays.asList("en", "en-US", "fr", "", "  ", null)) {
      var rows = map(language, row(1, "Fund A", ZH_A, EN_LINK, ZH_LINK));

      assertThat(rows.get(0).fundName()).as(String.valueOf(language)).isEqualTo("Fund A");
      assertThat(rows.get(0).factsheetUrl()).as(String.valueOf(language)).isEqualTo(EN_LINK);
    }
  }

  @Test
  void rowsAreOrderedByDisplayNameIgnoringCaseThenBySeq() {
    var order =
        seqs(
            "en",
            row(4, "beta Fund", null, null, null),
            row(3, "Alpha Fund", null, null, null),
            row(2, "ALPHA Fund", null, null, null),
            row(1, "Zeta Fund", null, null, null));

    assertThat(order).containsExactly(2, 3, 4, 1);
  }

  @Test
  void seqBreaksTiesBetweenNamesThatDifferOnlyInCase() {
    var order =
        seqs(
            "en",
            row(30, "alpha", null, null, null),
            row(10, "ALPHA", null, null, null),
            row(20, "Alpha", null, null, null));

    assertThat(order).containsExactly(10, 20, 30);
  }

  @Test
  void zhHkSortsByTheDisplayedNameWithEnglishFallbacksBeforeChineseNames() {
    var order =
        seqs(
            "zh_HK",
            row(1, "Alpha Fund", ZH_A, null, null),
            row(2, "Zeta Fund", ZH_B, null, null),
            row(3, "Beta Fund", null, null, null));

    assertThat(order).containsExactly(3, 2, 1);
  }

  @Test
  void englishSortingIgnoresTheChineseNames() {
    var order =
        seqs(
            "en",
            row(1, "Alpha Fund", ZH_B, null, null),
            row(2, "Zeta Fund", ZH_A, null, null));

    assertThat(order).containsExactly(1, 2);
  }

  @Test
  void sortingUsesPlainCharacterCodeOrderNotLocaleCollation() {
    var order =
        seqs(
            "en",
            row(1, "\u00c4pfel Fund", null, null, null),
            row(2, "Zeta Fund", null, null, null),
            row(3, "10 Fund", null, null, null),
            row(4, "9 Fund", null, null, null));

    assertThat(order).containsExactly(3, 4, 2, 1);
  }

  @Test
  void aNullOrBlankDisplayNameSortsFirstThenBySeq() {
    var order =
        seqs(
            "en",
            row(5, "Alpha Fund", null, null, null),
            row(4, "", null, null, null),
            row(3, null, null, null, null),
            row(2, "  ", null, null, null));

    assertThat(order).containsExactly(3, 4, 2, 5);
  }

  @Test
  void anEmptyResultGivesAnEmptyRowsListAndTheInputIsNotReordered() {
    assertThat(mapper.toResultResponse(new FundFactSheetResult(List.of()), "en").data().rows())
        .isEmpty();

    var input =
        new FundFactSheetResult(
            List.of(row(2, "Zeta", null, null, null), row(1, "Alpha", null, null, null)));
    mapper.toResultResponse(input, "en");

    assertThat(input.rows()).extracting(FundFactSheetRowResult::seq).containsExactly(2, 1);
  }

  @Test
  void theResultIsRequired() {
    assertThatThrownBy(() -> mapper.toResultResponse(null, "en"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("result");
  }
}

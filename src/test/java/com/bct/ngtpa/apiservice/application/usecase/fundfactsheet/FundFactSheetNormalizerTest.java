package com.bct.ngtpa.apiservice.application.usecase.fundfactsheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FundFactSheetResult;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.FundFactSheetRowResult;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.RawFundFactSheetData;
import com.bct.ngtpa.apiservice.application.dto.fundfactsheet.RawFundFactSheetRow;
import com.bct.ngtpa.apiservice.application.port.out.FundFactSheetPresentationEventPort;
import com.bct.ngtpa.apiservice.application.service.presentation.UrlValidator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FundFactSheetNormalizerTest {

  private static final String EN_LINK = "https://example.test/en.pdf";
  private static final String ZH_LINK = "https://example.test/zh.pdf";

  private final RecordingEvents events = new RecordingEvents();
  private final FundFactSheetNormalizer normalizer =
      new FundFactSheetNormalizer(new UrlValidator(events::malformedFactsheet), events);

  @Test
  void keepsRowsInDownstreamOrderWithEveryField() {
    var raw =
        data(
            row(30, "Fund C", "\u4e19\u57fa\u91d1", EN_LINK, ZH_LINK),
            row(10, "Fund A", "\u7532\u57fa\u91d1", EN_LINK, ZH_LINK),
            row(20, "Fund B", "\u4e59\u57fa\u91d1", EN_LINK, ZH_LINK));

    FundFactSheetResult result = normalizer.normalize(raw);

    assertThat(result.rows())
        .containsExactly(
            new FundFactSheetRowResult(30, "Fund C", "\u4e19\u57fa\u91d1", EN_LINK, ZH_LINK),
            new FundFactSheetRowResult(10, "Fund A", "\u7532\u57fa\u91d1", EN_LINK, ZH_LINK),
            new FundFactSheetRowResult(20, "Fund B", "\u4e59\u57fa\u91d1", EN_LINK, ZH_LINK));
    assertThat(events.recorded()).isEmpty();
  }

  @Test
  void emptyInputGivesAnEmptyResultAndNoEvent() {
    assertThat(normalizer.normalize(data()).rows()).isEmpty();
    assertThat(events.recorded()).isEmpty();
  }

  @Test
  void aRowWithoutSeqIsDroppedAndReportedOncePerRow() {
    var raw =
        data(
            row(null, "Dropped A", "", "/relative", "/relative"),
            row(1, "Kept", "", "", ""),
            row(null, "Dropped B", "", "", ""));

    FundFactSheetResult result = normalizer.normalize(raw);

    assertThat(result.rows()).extracting(FundFactSheetRowResult::seq).containsExactly(1);
    assertThat(events.recorded()).containsExactly("missingSeq", "missingSeq");
  }

  @Test
  void aDuplicateSeqKeepsTheFirstRowAndReportsEachDroppedRow() {
    var raw =
        data(
            row(5, "First", "first-zh", EN_LINK, ZH_LINK),
            row(6, "Other", "", "", ""),
            row(5, "Second", "second-zh", EN_LINK, ZH_LINK),
            row(5, "Third", "third-zh", EN_LINK, ZH_LINK));

    FundFactSheetResult result = normalizer.normalize(raw);

    assertThat(result.rows()).extracting(FundFactSheetRowResult::seq).containsExactly(5, 6);
    assertThat(result.rows().get(0).nameEng()).isEqualTo("First");
    assertThat(result.rows().get(0).nameChi()).isEqualTo("first-zh");
    assertThat(events.recorded()).containsExactly("duplicateSeq", "duplicateSeq");
  }

  @Test
  void aDroppedRowIsNotInspectedAnyFurther() {
    var raw =
        data(
            row(1, "Kept", "", "", ""),
            row(1, "", "", "/relative", "not a url"),
            row(null, null, null, "/relative", "not a url"));

    normalizer.normalize(raw);

    assertThat(events.recorded()).containsExactly("duplicateSeq", "missingSeq");
  }

  @Test
  void zeroAndNegativeSeqAreValidKeys() {
    var raw = data(row(0, "Zero", "", "", ""), row(-1, "Minus", "", "", ""));

    FundFactSheetResult result = normalizer.normalize(raw);

    assertThat(result.rows()).extracting(FundFactSheetRowResult::seq).containsExactly(0, -1);
    assertThat(events.recorded()).isEmpty();
  }

  @Test
  void aBlankOrNullEnglishNameIsKeptAsReceivedAndReported() {
    var raw =
        data(
            row(1, null, "", "", ""),
            row(2, "", "", "", ""),
            row(3, "   ", "", "", ""),
            row(4, "Real", "", "", ""));

    FundFactSheetResult result = normalizer.normalize(raw);

    assertThat(result.rows())
        .extracting(FundFactSheetRowResult::nameEng)
        .containsExactly(null, "", "   ", "Real");
    assertThat(events.recorded())
        .containsExactly("blankEnglishName", "blankEnglishName", "blankEnglishName");
  }

  @Test
  void aNullOrBlankChineseNameIsCarriedAsReceivedWithoutEvent() {
    var raw =
        data(row(1, "A", null, "", ""), row(2, "B", "", "", ""), row(3, "C", "  ", "", ""));

    FundFactSheetResult result = normalizer.normalize(raw);

    assertThat(result.rows())
        .extracting(FundFactSheetRowResult::nameChi)
        .containsExactly(null, "", "  ");
    assertThat(events.recorded()).isEmpty();
  }

  @Test
  void namesAreNotTrimmed() {
    var raw = data(row(1, " Fund A ", " zh ", "", ""));

    FundFactSheetRowResult row = normalizer.normalize(raw).rows().get(0);

    assertThat(row.nameEng()).isEqualTo(" Fund A ");
    assertThat(row.nameChi()).isEqualTo(" zh ");
  }

  @Test
  void validLinksAreKeptVerbatimWithoutEvent() {
    String odd = "HTTPS://EXAMPLE.TEST/A%20B?x=1&y=2#frag";
    var raw = data(row(1, "A", "", odd, "mailto:someone@example.test"));

    FundFactSheetRowResult row = normalizer.normalize(raw).rows().get(0);

    assertThat(row.linkEng()).isEqualTo(odd);
    assertThat(row.linkChi()).isEqualTo("mailto:someone@example.test");
    assertThat(events.recorded()).isEmpty();
  }

  @Test
  void anAbsentLinkBecomesNullWithoutEvent() {
    var raw = data(row(1, "A", "", null, ""), row(2, "B", "", "   ", null));

    FundFactSheetResult result = normalizer.normalize(raw);

    assertThat(result.rows())
        .extracting(FundFactSheetRowResult::linkEng)
        .containsExactly(null, null);
    assertThat(result.rows())
        .extracting(FundFactSheetRowResult::linkChi)
        .containsExactly(null, null);
    assertThat(events.recorded()).isEmpty();
  }

  @Test
  void aMalformedLinkBecomesNullAndIsReportedOncePerLink() {
    var raw =
        data(
            row(1, "A", "", "/relative", "not a url with secret=abc"),
            row(2, "B", "", EN_LINK, "/relative"));

    FundFactSheetResult result = normalizer.normalize(raw);

    assertThat(result.rows().get(0).linkEng()).isNull();
    assertThat(result.rows().get(0).linkChi()).isNull();
    assertThat(result.rows().get(1).linkEng()).isEqualTo(EN_LINK);
    assertThat(result.rows().get(1).linkChi()).isNull();
    assertThat(events.recorded())
        .containsExactly("malformedFactsheet", "malformedFactsheet", "malformedFactsheet");
  }

  @Test
  void aMixedInputIsNormalizedInOneInOrderPass() {
    var raw =
        data(
            row(3, "C", "", "/x", EN_LINK),
            row(null, "No seq", "", "", ""),
            row(3, "Duplicate", "", "", ""),
            row(1, " ", "zh", EN_LINK, ""));

    FundFactSheetResult result = normalizer.normalize(raw);

    assertThat(result.rows()).extracting(FundFactSheetRowResult::seq).containsExactly(3, 1);
    assertThat(events.recorded())
        .containsExactly("malformedFactsheet", "missingSeq", "duplicateSeq", "blankEnglishName");
  }

  @Test
  void constructorAndInputRequireNonNull() {
    var validator = new UrlValidator(events::malformedFactsheet);

    assertThatThrownBy(() -> new FundFactSheetNormalizer(null, events))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("urlValidator");
    assertThatThrownBy(() -> new FundFactSheetNormalizer(validator, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("events");
    assertThatThrownBy(() -> normalizer.normalize(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("raw");
  }

  private static RawFundFactSheetData data(RawFundFactSheetRow... rows) {
    return new RawFundFactSheetData(List.of(rows));
  }

  private static RawFundFactSheetRow row(
      Integer seq, String nameEng, String nameChi, String linkEng, String linkChi) {
    return new RawFundFactSheetRow(seq, nameEng, nameChi, linkEng, linkChi);
  }

  /** Records the order in which presentation events are reported. */
  private static final class RecordingEvents implements FundFactSheetPresentationEventPort {
    private final List<String> recorded = new ArrayList<>();

    @Override
    public void missingSeq() {
      recorded.add("missingSeq");
    }

    @Override
    public void duplicateSeq() {
      recorded.add("duplicateSeq");
    }

    @Override
    public void blankEnglishName() {
      recorded.add("blankEnglishName");
    }

    @Override
    public void malformedFactsheet() {
      recorded.add("malformedFactsheet");
    }

    @Override
    public void missingAccountIdentifier() {
      recorded.add("missingAccountIdentifier");
    }

    List<String> recorded() {
      return recorded;
    }
  }
}

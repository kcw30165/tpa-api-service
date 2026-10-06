package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimDataItem.FundInfoEntry;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto.TrpGetFundPriceApimDataItem.FundPriceEntry;
import com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Fail-first tests for {@code FundPriceApimContentValidator.reconcile(...)} -- the FundPrice x
 * FundInfo join by fund-code, confirmed byte-exact from fund-price-apim-contract.md's Operation 2
 * section (script 092): NOT a flat single-item validation like
 * {@code FundPriceModeApimContentValidator.normalize(...)}, but a real reconciliation across two
 * arrays inside one wrapper item.
 *
 * <p>Confirmed design decisions under test:
 *
 * <ul>
 *   <li>A FundInfo entry with no matching FundPrice row is skipped, NOT an error (the reverse --
 *       a FundPrice row with no matching FundInfo -- fails closed, per the doc's invariant
 *       "every FundPrice record resolves to exactly one FundInfo record").
 *   <li>price-date is downstream ISO format ({@code yyyy-MM-dd}), confirmed from the doc's own
 *       byte-exact sample, parsed via a fixed formatter, not the configurable dd/MM/yyyy format.
 * </ul>
 */
class FundPriceApimContentValidatorTest {

  private static FundPriceEntry price(String fundCode, String priceDate, String bid, String offer) {
    return new FundPriceEntry(fundCode, priceDate, new BigDecimal(bid), new BigDecimal(offer));
  }

  private static FundInfoEntry info(String fundCode, String riskLevel) {
    return new FundInfoEntry(fundCode, fundCode + " Fund", fundCode + " Fund CN", 1, "HKD", riskLevel);
  }

  // ----- happy path: reconciliation joins correctly ---------------------------

  @Test
  void reconcilesMatchingFundPriceAndFundInfoByFundCode() {
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(price("DUSM", "2026-05-05", "159.7628", "159.7628")),
            List.of(info("DUSM", "5")));

    var result = FundPriceApimContentValidator.reconcile(item);

    assertThat(result.rows()).hasSize(1);
    var row = result.rows().get(0);
    assertThat(row.fundCode()).isEqualTo("DUSM");
    assertThat(row.fundName()).isEqualTo("DUSM Fund");
    assertThat(row.priceDate()).isEqualTo(LocalDate.of(2026, 5, 5));
    assertThat(row.bidPrice()).isEqualByComparingTo("159.7628");
    assertThat(row.offerPrice()).isEqualByComparingTo("159.7628");
    assertThat(row.riskLevel()).isEqualTo("5");
  }

  @Test
  void parsesIsoFormattedPriceDateNotTheDdMmYyyyFormat() {
    // Confirms price-date is parsed as ISO (yyyy-MM-dd), matching the doc's exact sample
    // "price-date": "2026-05-05" -- NOT dd/MM/yyyy like available-fund-price-dates.
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(price("DUSM", "2026-12-31", "1", "1")), List.of(info("DUSM", "")));

    var result = FundPriceApimContentValidator.reconcile(item);

    assertThat(result.rows().get(0).priceDate()).isEqualTo(LocalDate.of(2026, 12, 31));
  }

  @Test
  void blankRiskLevelIsAcceptedAsLegitimate() {
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(price("DUSM", "2026-05-05", "1", "1")), List.of(info("DUSM", "")));

    var result = FundPriceApimContentValidator.reconcile(item);

    assertThat(result.rows().get(0).riskLevel()).isEqualTo("");
  }

  // ----- FundInfo-only orphan: skipped, not an error ---------------------------

  @Test
  void fundInfoWithNoMatchingFundPriceIsSkippedNotAnError() {
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(price("DUSM", "2026-05-05", "1", "1")),
            List.of(info("DUSM", "1"), info("ORPHAN", "2")));

    var result = FundPriceApimContentValidator.reconcile(item);

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).fundCode()).isEqualTo("DUSM");
    // ORPHAN never appears in output -- confirms it was skipped, not errored.
  }

  // ----- FundPrice-only orphan: fails closed (the REQUIRED direction) ----------

  @Test
  void fundPriceWithNoMatchingFundInfoFailsClosed() {
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(price("UNMATCHED", "2026-05-05", "1", "1")), List.of(info("DUSM", "1")));

    assertThatThrownBy(() -> FundPriceApimContentValidator.reconcile(item))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  // ----- uniqueness invariants --------------------------------------------------

  @Test
  void duplicateFundCodeInFundInfoFailsClosed() {
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(price("DUSM", "2026-05-05", "1", "1")),
            List.of(info("DUSM", "1"), info("DUSM", "2")));

    assertThatThrownBy(() -> FundPriceApimContentValidator.reconcile(item))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void duplicateFundCodePriceDatePairInFundPriceFailsClosed() {
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(
                price("DUSM", "2026-05-05", "1", "1"), price("DUSM", "2026-05-05", "2", "2")),
            List.of(info("DUSM", "1")));

    assertThatThrownBy(() -> FundPriceApimContentValidator.reconcile(item))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void sameFundCodeDifferentPriceDatesIsLegitimateNotADuplicate() {
    // Confirms the MONTHLY/export case: the same fund-code appearing once per price date
    // across a range is explicitly legitimate per the doc's reconciliation invariants.
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(
                price("DUSM", "2026-05-01", "1", "1"), price("DUSM", "2026-05-02", "2", "2")),
            List.of(info("DUSM", "1")));

    var result = FundPriceApimContentValidator.reconcile(item);

    assertThat(result.rows()).hasSize(2);
  }

  // ----- required-field / malformed-value invariants ----------------------------

  @Test
  void blankFundCodeInFundPriceFailsClosed() {
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(price("", "2026-05-05", "1", "1")), List.of(info("DUSM", "1")));

    assertThatThrownBy(() -> FundPriceApimContentValidator.reconcile(item))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void unparseablePriceDateFailsClosed() {
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(price("DUSM", "05/05/2026", "1", "1")), // wrong format, not ISO
            List.of(info("DUSM", "1")));

    assertThatThrownBy(() -> FundPriceApimContentValidator.reconcile(item))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void blankFundNameInFundInfoFailsClosed() {
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(price("DUSM", "2026-05-05", "1", "1")),
            List.of(new FundInfoEntry("DUSM", "", "chin", 1, "HKD", "1")));

    assertThatThrownBy(() -> FundPriceApimContentValidator.reconcile(item))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void riskLevelOutsideSupportedSetFailsClosed() {
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(price("DUSM", "2026-05-05", "1", "1")), List.of(info("DUSM", "7.5")));

    assertThatThrownBy(() -> FundPriceApimContentValidator.reconcile(item))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void nullFundPriceListFailsClosed() {
    var item = new TrpGetFundPriceApimDataItem(null, List.of(info("DUSM", "1")));

    assertThatThrownBy(() -> FundPriceApimContentValidator.reconcile(item))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void nullFundInfoListFailsClosed() {
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(price("DUSM", "2026-05-05", "1", "1")), null);

    assertThatThrownBy(() -> FundPriceApimContentValidator.reconcile(item))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void emptyFundPriceListFailsClosed() {
    // FLAGGED INFERENCE (not independently confirmed against the docs): treated as
    // "missing" required data per the invariants' "Null, blank, duplicate, missing...
    // are invalid downstream contracts" framing. Open for correction if this is wrong --
    // the downstream err-message path (err.tpa.no.fund.price) may be the ONLY intended
    // signal for a genuine empty-range case, in which case this test's expectation
    // should change to accept an empty result instead of throwing.
    var item = new TrpGetFundPriceApimDataItem(List.of(), List.of(info("DUSM", "1")));

    assertThatThrownBy(() -> FundPriceApimContentValidator.reconcile(item))
        .isInstanceOf(FundPriceRetrievalException.class);
  }
  // ----- coverage closure (script 095): previously untested branches ----------

  @Test
  void reconcileNullItemFailsClosed() {
    assertThatThrownBy(() -> FundPriceApimContentValidator.reconcile(null))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void emptyFundInfoListFailsClosed() {
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(price("DUSM", "2026-05-05", "1", "1")), List.of());

    assertThatThrownBy(() -> FundPriceApimContentValidator.reconcile(item))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void nullFundCodeInFundPriceFailsClosed() {
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(price(null, "2026-05-05", "1", "1")), List.of(info("DUSM", "1")));

    assertThatThrownBy(() -> FundPriceApimContentValidator.reconcile(item))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void nullFundPriceEntryInListFailsClosed() {
    var item =
        new TrpGetFundPriceApimDataItem(
            java.util.Arrays.asList(price("DUSM", "2026-05-05", "1", "1"), null),
            List.of(info("DUSM", "1")));

    assertThatThrownBy(() -> FundPriceApimContentValidator.reconcile(item))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void nullBidPriceInFundPriceEntryFailsClosed() {
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(new FundPriceEntry("DUSM", "2026-05-05", null, new BigDecimal("1"))),
            List.of(info("DUSM", "1")));

    assertThatThrownBy(() -> FundPriceApimContentValidator.reconcile(item))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void nullOfferPriceInFundPriceEntryFailsClosed() {
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(new FundPriceEntry("DUSM", "2026-05-05", new BigDecimal("1"), null)),
            List.of(info("DUSM", "1")));

    assertThatThrownBy(() -> FundPriceApimContentValidator.reconcile(item))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void nullPriceDateFailsClosed() {
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(price("DUSM", null, "1", "1")), List.of(info("DUSM", "1")));

    assertThatThrownBy(() -> FundPriceApimContentValidator.reconcile(item))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void blankPriceDateFailsClosed() {
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(price("DUSM", "  ", "1", "1")), List.of(info("DUSM", "1")));

    assertThatThrownBy(() -> FundPriceApimContentValidator.reconcile(item))
        .isInstanceOf(FundPriceRetrievalException.class);
  }

  @Test
  void nullRiskLevelIsAcceptedAsLegitimate() {
    // Distinct from blankRiskLevelIsAcceptedAsLegitimate above: riskLevel == null takes a
    // different short-circuit path through validateRiskLevel's OR condition than
    // riskLevel.isEmpty() does, and both must be independently exercised for branch
    // coverage.
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(price("DUSM", "2026-05-05", "1", "1")), List.of(info("DUSM", null)));

    var result = FundPriceApimContentValidator.reconcile(item);

    assertThat(result.rows().get(0).riskLevel()).isNull();
  }

  @Test
  void zeroRiskLevelIsAcceptedAsNoRiskLevel() {
    var item =
        new TrpGetFundPriceApimDataItem(
            List.of(price("DUSM", "2026-05-05", "1", "1")), List.of(info("DUSM", "0")));

    var result = FundPriceApimContentValidator.reconcile(item);

    assertThat(result.rows().get(0).riskLevel()).isEqualTo("0");
  }
}

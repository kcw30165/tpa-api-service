package com.bct.ngtpa.apiservice.adapter.out.apim.fundprice.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Fail-first: {@code TrpGetFundPriceApimRequest} does not yet carry a {@code fund-list} field.
 *
 * <p>Confirmed design (docs/design/9-fund-price/fund-price-decisions-open-questions.md,
 * 2026-10-15): {@code fundList} is a comma-joined fund-code string, always {@code ""} for all
 * current (non-comparison) usages. This test only confirms the wire shape; population is the
 * adapter's responsibility, exercised separately in {@code ApimFundPriceSourceAdapterTest}.
 */
class TrpGetFundPriceApimRequestTest {

  @Test
  void carriesFundListAlongsideTheExistingThreeFields() {
    var request = new TrpGetFundPriceApimRequest("00000008802", "05/05/2026", "05/05/2026", "");

    assertThat(request.policyNo()).isEqualTo("00000008802");
    assertThat(request.fromDate()).isEqualTo("05/05/2026");
    assertThat(request.toDate()).isEqualTo("05/05/2026");
    assertThat(request.fundList()).isEqualTo("");
  }

  @Test
  void serializesFundListAsTheRawDownstreamFundListPropertyInDeclaredOrder() throws Exception {
    var request = new TrpGetFundPriceApimRequest("00000008802", "05/05/2026", "05/05/2026", "");

    String json = new ObjectMapper().writeValueAsString(request);

    assertThat(json)
        .isEqualTo(
            "{\"policy-no\":\"00000008802\",\"from-date\":\"05/05/2026\","
                + "\"to-date\":\"05/05/2026\",\"fund-list\":\"\"}");
  }

  @Test
  void nonblankFundListIsCarriedThroughVerbatimForFutureFundComparisonUsage() {
    // Confirms the record itself places no constraint on fundList's content -- comma-joining FE
    // fund codes into this field is deliberately out of scope for this change (confirmed
    // decision, docs/design/9-fund-price/fund-price-decisions-open-questions.md) and belongs to
    // a later fund-comparison feature.
    var request =
        new TrpGetFundPriceApimRequest(
            "00000008802", "05/05/2026", "05/05/2026", "fundA,fundB,fundC");

    assertThat(request.fundList()).isEqualTo("fundA,fundB,fundC");
  }
}

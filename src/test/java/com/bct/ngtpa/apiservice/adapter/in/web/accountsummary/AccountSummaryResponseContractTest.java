package com.bct.ngtpa.apiservice.adapter.in.web.accountsummary;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary.AccountOverviewResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary.AccountOverviewTotalsResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary.CurrencySelectionResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary.AccountSummaryDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary.AccountSummaryResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary.GainLossBreakdownHoldingResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.accountsummary.GainLossBreakdownResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountSummaryResponseContractTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void accountNumberIsCamelCaseAndCarriesThePaddedJoinedIdentifiers() throws Exception {
    var data =
        new AccountSummaryDataResponse("00000008802-0230", "31/08/2026", null, null, null, null);

    var json = mapper.readTree(mapper.writeValueAsString(data));

    assertThat(json.has("accountNumber")).isTrue();
    assertThat(json.has("account-number")).isFalse();
    assertThat(json.path("accountNumber").asText()).isEqualTo("00000008802-0230");
    assertThat(json.path("accountNumber").asText()).hasSize(16);
  }

  @Test
  void breakdownHoldingExposesTheApprovedFieldNames() throws Exception {
    var holding =
        new GainLossBreakdownHoldingResponse(
            "DGLE", "BlackRock Global Equity Fund", 80, "3415434.27", "25.0", "5092583.02");

    var json = mapper.readTree(mapper.writeValueAsString(holding));

    assertThat(json.fieldNames())
        .toIterable()
        .containsExactly(
            "fundCode", "fundName", "fundSequence", "balance", "portfolioPercentage", "gainLoss");
  }

  @Test
  void monetaryValuesAreStringsSoPrecisionSurvivesTheWire() throws Exception {
    var holding =
        new GainLossBreakdownHoldingResponse("DGLE", "Fund", 80, "3415434.27", "25.0", "-812.50");

    var raw = mapper.writeValueAsString(holding);

    assertThat(raw).contains("\"balance\":\"3415434.27\"");
    assertThat(raw).contains("\"portfolioPercentage\":\"25.0\"");
    assertThat(raw).contains("\"gainLoss\":\"-812.50\"");
  }

  @Test
  void breakdownSectionCarriesPeriodTotalsAndHoldings() throws Exception {
    var breakdown =
        new GainLossBreakdownResponse(
            "SINCE_SCHEME_INCEPTION",
            "31/08/2026",
            "13661737.08",
            "5092583.02",
            List.of(
                new GainLossBreakdownHoldingResponse(
                    "DGLE", "Fund", 80, "3415434.27", "25.0", "5092583.02")));

    var json = mapper.readTree(mapper.writeValueAsString(breakdown));

    assertThat(json.fieldNames())
        .toIterable()
        .containsExactly("period", "gainLossAsOfDate", "totalValue", "totalGainLoss", "holdings");
    assertThat(json.path("holdings")).hasSize(1);
    assertThat(json.path("period").asText()).isEqualTo("SINCE_SCHEME_INCEPTION");
  }

  @Test
  void nullHoldingsBecomeAnEmptyListRatherThanNull() throws Exception {
    var breakdown =
        new GainLossBreakdownResponse("YEAR_TO_DATE", "31/08/2026", "0.00", "0.00", null);

    assertThat(breakdown.holdings()).isEmpty();
    assertThat(mapper.readTree(mapper.writeValueAsString(breakdown)).path("holdings")).isEmpty();
  }

  @Test
  void soldFundRowIsZeroRatherThanNullOnBothAggregatedValues() throws Exception {
    // A fund in TRPGetInvestGL with no accBal rows: the empty sum is zero on
    // balance and percentage, while gain/loss is still reported.
    var sold =
        new GainLossBreakdownHoldingResponse("SOLD", "Sold Fund", 90, "0.00", "0.0", "412.30");

    var json = mapper.readTree(mapper.writeValueAsString(sold));

    assertThat(json.path("balance").asText()).isEqualTo("0.00");
    assertThat(json.path("portfolioPercentage").asText()).isEqualTo("0.0");
    assertThat(json.path("gainLoss").asText()).isEqualTo("412.30");
  }

  @Test
  void summaryDataOrdersAccountNumberFirstAndBreakdownLast() throws Exception {
    var data =
        new AccountSummaryDataResponse(
            "00000008802-0230",
            "31/08/2026",
            null,
            null,
            null,
            new GainLossBreakdownResponse(
                "SINCE_SCHEME_INCEPTION", "31/08/2026", "0.00", "0.00", List.of()));

    var json = mapper.readTree(mapper.writeValueAsString(data));

    assertThat(json.fieldNames())
        .toIterable()
        .containsExactly(
            "accountNumber",
            "asOfDate",
            "currencySelection",
            "accountOverview",
            "investmentPortfolio",
            "gainLossBreakdown");
  }

  @Test
  void reusedBalanceSectionsKeepTheirOwnContractIncludingAlwaysIncludedTotals() throws Exception {
    // Composition by reference: the balance sections must serialize exactly as
    // they do on /api/v1/account/balance, including the ALWAYS inclusion on
    // pendingSwitchIn, which would be lost if the fields had been redeclared.
    var data =
        new AccountSummaryDataResponse(
            "00000008802-0230",
            "31/08/2026",
            new CurrencySelectionResponse("HKD", List.of("HKD", "USD")),
            new AccountOverviewResponse(
                null, List.of(), new AccountOverviewTotalsResponse("13661737.08", null)),
            null,
            null);

    var json = mapper.readTree(mapper.writeValueAsString(data));

    assertThat(json.path("currencySelection").path("selectedCurrency").asText()).isEqualTo("HKD");
    assertThat(json.path("currencySelection").path("availableCurrencies")).hasSize(2);
    assertThat(json.path("accountOverview").path("accountTotals").path("totalBalance").asText())
        .isEqualTo("13661737.08");
    assertThat(json.path("accountOverview").path("accountTotals").has("pendingSwitchIn")).isTrue();
  }

  @Test
  void resultEnvelopeNestsDataUnderTheSharedContract() throws Exception {
    var result =
        new AccountSummaryResultResponse(
            new AccountSummaryDataResponse(
                "00000008802-0230", "31/08/2026", null, null, null, null));

    var json = mapper.readTree(mapper.writeValueAsString(result));

    assertThat(json.has("data")).isTrue();
    assertThat(json.path("data").path("accountNumber").asText()).isEqualTo("00000008802-0230");
  }
}

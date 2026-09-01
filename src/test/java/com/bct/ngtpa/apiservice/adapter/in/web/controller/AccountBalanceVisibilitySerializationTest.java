package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.accountbalance.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AccountBalanceVisibilitySerializationTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void overviewNullsRemainWhilePortfolioNullsAreOmitted() throws Exception {
    String o =
        mapper.writeValueAsString(
            new AccountOverviewResponse(
                new AccountOverviewDisplayOptionsResponse(false, false),
                java.util.List.of(
                    new AccountOverviewDisplayAccountResponse("EE", "Member", 1, "1.00", null)),
                new AccountOverviewTotalsResponse("1.00", null)));
    String f =
        mapper.writeValueAsString(
            new SubDisplayLevelFundResponse("F", "Fund", "1.00", "1.00", "1.00", null, null));
    assertThat(o).contains("\"vestedBalance\":null", "\"pendingSwitchIn\":null");
    assertThat(f).doesNotContain("fxRate", "factsheetUrl");
  }

  @Test
  void visibleValuesSerialize() throws Exception {
    String f =
        mapper.writeValueAsString(
            new SubDisplayLevelFundResponse(
                "F", "Fund", "1.00", "1.00", "1.00", "https://example.test/f", "1.0000"));
    assertThat(f).contains("fxRate", "factsheetUrl");
  }
}

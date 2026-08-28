package com.bct.ngtpa.apiservice.adapter.in.web.controller;
import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.adapter.in.web.response.accountbalance.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
class AccountBalanceVisibilitySerializationTest {
 private final ObjectMapper mapper=new ObjectMapper();
 @Test void overviewNullsRemainWhilePortfolioNullsAreOmitted() throws Exception {String overview=mapper.writeValueAsString(new AccountOverviewResponse("Display Account",null,null));String item=mapper.writeValueAsString(new InvestmentPortfolioItemResponse("Sub","Fund",null,null));assertThat(overview).contains("\"vestedBalance\":null","\"pendingSwitchIn\":null");assertThat(item).doesNotContain("fxRate","factsheetUrl");}
 @Test void visiblePortfolioValuesAreSerialized() throws Exception {String item=mapper.writeValueAsString(new InvestmentPortfolioItemResponse("Sub","Fund","1.0000","https://example.test/f"));assertThat(item).contains("\"fxRate\":\"1.0000\"","\"factsheetUrl\":\"https://example.test/f\"");}
}

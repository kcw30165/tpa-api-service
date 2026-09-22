package com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss.dto.TrpGetInvestGlApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss.dto.TrpGetInvestGlApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss.dto.TrpGetInvestGlFund;
import com.bct.ngtpa.apiservice.application.dto.accountgainloss.AccountGainLossPeriod;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountGainLossApimContractTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void mapsBusinessPeriodsOnlyAtAdapterBoundary() {
    assertThat(
            AccountGainLossApimModeMapper.toWireValue(AccountGainLossPeriod.SINCE_SCHEME_INCEPTION))
        .isEqualTo("ssi");
    assertThat(AccountGainLossApimModeMapper.toWireValue(AccountGainLossPeriod.YEAR_TO_DATE))
        .isEqualTo("ytd");
  }

  @Test
  void serializesExactHyphenatedRequestContract() throws Exception {
    var request =
        new TrpGetInvestGlApimRequest(
            "db230", "role_user_mbr", "00000008802", "230", "01/09/2026", "ssi", "");
    assertThat(mapper.writeValueAsString(request))
        .isEqualTo(
            "{\"login-user-id\":\"db230\",\"login-user-role\":\"role_user_mbr\",\"policy-no\":\"00000008802\",\"cert-no\":\"230\",\"ref-date\":\"01/09/2026\",\"mode\":\"ssi\",\"curr\":\"\"}");
  }

  @Test
  void serializesSelectedCurrencyAndNormalizesNullToEmpty() throws Exception {
    var selected =
        new TrpGetInvestGlApimRequest(
            "db230", "role_user_mbr", "00000008802", "230", "01/09/2026", "ssi", "USD");
    assertThat(mapper.readTree(mapper.writeValueAsString(selected)).path("curr").asText())
        .isEqualTo("USD");

    var unspecified =
        new TrpGetInvestGlApimRequest(
            "db230", "role_user_mbr", "00000008802", "230", "01/09/2026", "ssi", null);
    assertThat(mapper.readTree(mapper.writeValueAsString(unspecified)).path("curr").asText())
        .isEmpty();
  }

  @Test
  void validatesOneCompleteDataItemAndAllowsEmptyFundList() {
    var empty = new TrpGetInvestGlApimDataItem(LocalDate.of(2026, 5, 27), List.of());
    assertThat(AccountGainLossApimContentValidator.requireSingleData(List.of(empty)))
        .isSameAs(empty);
    assertThatThrownBy(() -> AccountGainLossApimContentValidator.requireSingleData(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsDuplicateOrIncompleteFunds() {
    var fund = new TrpGetInvestGlFund("DGLE", "Fund", "基金", 80, new BigDecimal("1.00"));
    var duplicate = new TrpGetInvestGlApimDataItem(LocalDate.of(2026, 5, 27), List.of(fund, fund));
    assertThatThrownBy(
            () -> AccountGainLossApimContentValidator.requireSingleData(List.of(duplicate)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

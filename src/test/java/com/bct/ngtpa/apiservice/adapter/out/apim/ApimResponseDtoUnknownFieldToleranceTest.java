package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto.TrpGetMemberAccBalApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.dto.TrpResetPasswordApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpRegMemberApimDataItem;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Additive downstream fields at any nested level parse with the strict production mapper
 * (JacksonConfig keeps FAIL_ON_UNKNOWN_PROPERTIES enabled) for the DTOs that lacked ignoreUnknown
 * before 2026-10-13.
 */
class ApimResponseDtoUnknownFieldToleranceTest {

  // Mirrors JacksonConfig#objectMapper.
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void accountBalanceToleratesUnknownFieldsAtEveryNestedLevel() throws Exception {
    String json =
        """
        {"response":{"err-message":"","data":[{
          "as-of-date":"2026-05-27","future-item":"x",
          "displayOptions":[{"vestedBalanceVisible":true,"pendingSwitchInVisible":false,"fxRateVisible":true,"future":1}],
          "curr":[{"avai":["HKD"],"selected":"HKD","future":"x"}],
          "dispSrc":[{"disp-src":"EE","src-desc":"E","src-chin-desc":"E","sub-disp-src":"OE",
            "sub-src-desc":"M","sub-src-chin-desc":"M","seq":50,"future":"x"}],
          "fundDtl":[{"fund-code":"F1","fund-name":"F","chin-fund-name":"F","fund-seq":1,
            "fund-price":1.0,"fund-curr":"HKD","exchange-rate":1.0,"fund-fact-sheet":"https://e.test/en.pdf",
            "chin-fund-fact-sheet":"https://e.test/zh.pdf","future":"x"}],
          "accBal":[{"disp-src":"EE","sub-disp-src":"OE","fund-code":"F1","unit":1.0,"nav":2.0,
            "vested-bal":2.0,"percentage":100.0,"transit-amt":0.0,"future":"x"}]}]}}
        """;

    ApimResponseEnvelope<TrpGetMemberAccBalApimDataItem> envelope =
        mapper.readValue(json, envelopeOf(TrpGetMemberAccBalApimDataItem.class));

    var item = envelope.getResponse().getData().getFirst();
    assertThat(item.fundDetails().getFirst().fundCode()).isEqualTo("F1");
    assertThat(item.displaySources().getFirst().subDisplaySource()).isEqualTo("OE");
    assertThat(item.accountBalances().getFirst().percentage()).isEqualByComparingTo("100.0");
  }

  @Test
  void registrationAndForgotPasswordItemsTolerateUnknownFields() throws Exception {
    var chk =
        mapper.readValue(
            "{\"cust-id\":\"u\",\"isExist\":true,\"future\":\"x\"}",
            TrpChkCustIdApimDataItem.class);
    var reg =
        mapper.readValue(
            "{\"success\":true,\"cust-id\":\"u\",\"future\":\"x\"}",
            TrpRegMemberApimDataItem.class);
    var reset =
        mapper.readValue(
            "{\"success\":true,\"cust-id\":\"u\",\"future\":\"x\"}",
            TrpResetPasswordApimDataItem.class);

    assertThat(chk.exists()).isTrue();
    assertThat(reg.success()).isTrue();
    assertThat(reset.success()).isTrue();
  }

  @Test
  void personalInformationUpdateToleratesUnknownFieldsIncludingNestedErrors() throws Exception {
    var item =
        mapper.readValue(
            "{\"success\":false,\"ref-no\":\"R\",\"future\":\"x\","
                + "\"errors\":[{\"type\":\"T\",\"fields\":\"f\",\"code\":\"C\",\"future\":\"x\"}]}",
            UpdateMemberInfoApimDataItem.class);

    assertThat(item.getRefNo()).isEqualTo("R");
    assertThat(item.getErrors().getFirst().getCode()).isEqualTo("C");
  }

  private JavaType envelopeOf(Class<?> dataType) {
    return mapper.getTypeFactory().constructParametricType(ApimResponseEnvelope.class, dataType);
  }
}

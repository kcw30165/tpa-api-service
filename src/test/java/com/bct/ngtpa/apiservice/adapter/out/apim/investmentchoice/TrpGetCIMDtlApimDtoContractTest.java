package com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.investmentchoice.dto.TrpGetCIMDtlApimRequest;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TrpGetCIMDtlApimDtoContractTest {

  private final ObjectMapper json = new ObjectMapper();

  private static final String SAMPLE =
      """
      {
        "top-extra": "ignored",
        "response": {
          "body-extra": "ignored",
          "err-message": "",
          "data": [{
            "item-extra": "ignored",
            "interval-pcnt": 5,
            "SwitchSrc": [{
              "src-extra": "ignored",
              "switch-src": "EE",
              "src-desc": "Source English",
              "src-chin-desc": "Source Chinese",
              "seq": 10,
              "avail-sw": true
            }],
            "SwitchFund": [{
              "fund-extra": "ignored",
              "fund-code": "ZZF1",
              "fund-name": "Fund One",
              "chin-fund-name": "Fund Chinese",
              "fund-seq": 20,
              "risk-level": "3",
              "avail-sw": false
            }],
            "InvestMandate": [{
              "mandate-extra": "ignored",
              "effect-date": "05/11/2026",
              "switch-src": "EE",
              "fund-code": "ZZF1",
              "pcnt": "60"
            }],
            "PendingReq": [{
              "pending-extra": "ignored",
              "ref-no": "REF-1",
              "submit-date": "05/11/2026",
              "submit-time": "17:04:14"
            }],
            "PendingReqDtl": [{
              "detail-extra": "ignored",
              "ref-no": "REF-1",
              "switch-src": "EE",
              "fund-code": "ZZF1",
              "pcnt": 40
            }]
          }]
        }
      }
      """;

  @Test
  void serializesRequestWithTheExactWireNamesAndStringValues() throws Exception {
    var request = new TrpGetCIMDtlApimRequest("00000008802", "0230", "05/11/2026");

    assertThat(json.writeValueAsString(request))
        .isEqualTo(
            "{\"policy-no\":\"00000008802\",\"cert-no\":\"0230\",\"ref-date\":\"05/11/2026\"}");
  }

  @Test
  void deserializesEveryFieldFromTheFullEnvelopeAndIgnoresUnknownPropertiesAtEveryLevel()
      throws Exception {
    JavaType type =
        json.getTypeFactory()
            .constructParametricType(ApimResponseEnvelope.class, TrpGetCIMDtlApimDataItem.class);

    ApimResponseEnvelope<TrpGetCIMDtlApimDataItem> envelope = json.readValue(SAMPLE, type);

    assertThat(envelope.getResponse().getErrMessage()).isEmpty();
    var item = envelope.getResponse().getData().get(0);
    assertThat(item.intervalPcnt()).isEqualTo(5);

    assertThat(item.switchSrc()).hasSize(1);
    var source = item.switchSrc().get(0);
    assertThat(source.switchSrc()).isEqualTo("EE");
    assertThat(source.srcDesc()).isEqualTo("Source English");
    assertThat(source.srcChinDesc()).isEqualTo("Source Chinese");
    assertThat(source.seq()).isEqualTo(10);
    assertThat(source.availSw()).isEqualTo(true);

    assertThat(item.switchFund()).hasSize(1);
    var fund = item.switchFund().get(0);
    assertThat(fund.fundCode()).isEqualTo("ZZF1");
    assertThat(fund.fundName()).isEqualTo("Fund One");
    assertThat(fund.chinFundName()).isEqualTo("Fund Chinese");
    assertThat(fund.fundSeq()).isEqualTo(20);
    assertThat(fund.riskLevel()).isEqualTo("3");
    assertThat(fund.availSw()).isEqualTo(false);

    assertThat(item.investMandate()).hasSize(1);
    var mandate = item.investMandate().get(0);
    assertThat(mandate.effectDate()).isEqualTo("05/11/2026");
    assertThat(mandate.switchSrc()).isEqualTo("EE");
    assertThat(mandate.fundCode()).isEqualTo("ZZF1");
    assertThat(mandate.pcnt()).isEqualTo("60").isInstanceOf(String.class);

    assertThat(item.pendingReq()).hasSize(1);
    var pending = item.pendingReq().get(0);
    assertThat(pending.refNo()).isEqualTo("REF-1");
    assertThat(pending.submitDate()).isEqualTo("05/11/2026");
    assertThat(pending.submitTime()).isEqualTo("17:04:14");

    assertThat(item.pendingReqDtl()).hasSize(1);
    var detail = item.pendingReqDtl().get(0);
    assertThat(detail.refNo()).isEqualTo("REF-1");
    assertThat(detail.switchSrc()).isEqualTo("EE");
    assertThat(detail.fundCode()).isEqualTo("ZZF1");
    assertThat(detail.pcnt()).isEqualTo(40).isInstanceOf(Integer.class);
    assertThat(source.availSw()).isInstanceOf(Boolean.class);
    assertThat(fund.availSw()).isInstanceOf(Boolean.class);
  }
}

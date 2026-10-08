package com.bct.ngtpa.apiservice.adapter.out.apim.fundfactsheet;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundfactsheet.dto.TrpGetFundFactSheetApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundfactsheet.dto.TrpGetFundFactSheetApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.fundfactsheet.dto.TrpGetFundFactSheetApimRow;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class FundFactSheetApimDtoContractTest {

  private final ObjectMapper json = new ObjectMapper();

  private static final String SAMPLE =
      "{\"response\":{\"err-message\":\"\",\"data\":[{\"FundFactSheet\":["
          + "{\"seq\":10010,\"name-eng\":\"Fund A\",\"name-chi\":\"\u57fa\u91d1\","
          + "\"link-eng\":\"https://example.test/a.pdf\",\"link-chi\":\"https://example.test/b.pdf\"},"
          + "{\"seq\":10020,\"name-eng\":\"Fund B\",\"name-chi\":\"\","
          + "\"link-eng\":\"\",\"link-chi\":\"\"}]}]}}";

  @Test
  void serializesTheRequestWithExactWireNamesAndOrder() throws Exception {
    var request = new TrpGetFundFactSheetApimRequest("00000008802", "05/05/2026");

    assertThat(json.writeValueAsString(request))
        .isEqualTo("{\"policy-no\":\"00000008802\",\"ref-date\":\"05/05/2026\"}");
  }

  @Test
  void deserializesTheFullEnvelopeThroughTheSameGenericTypeTheExecutorUses() throws Exception {
    JavaType type =
        json.getTypeFactory()
            .constructParametricType(ApimResponseEnvelope.class, TrpGetFundFactSheetApimDataItem.class);

    ApimResponseEnvelope<TrpGetFundFactSheetApimDataItem> envelope = json.readValue(SAMPLE, type);

    assertThat(envelope.getResponse().getErrMessage()).isEqualTo("");
    var rows = envelope.getResponse().getData().get(0).fundFactSheet();
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).seq()).isEqualTo(10010);
    assertThat(rows.get(0).nameEng()).isEqualTo("Fund A");
    assertThat(rows.get(0).nameChi()).isEqualTo("\u57fa\u91d1");
    assertThat(rows.get(0).linkEng()).isEqualTo("https://example.test/a.pdf");
    assertThat(rows.get(0).linkChi()).isEqualTo("https://example.test/b.pdf");
    assertThat(rows.get(1).seq()).isEqualTo(10020);
    assertThat(rows.get(1).nameChi()).isEqualTo("");
    assertThat(rows.get(1).linkEng()).isEqualTo("");
  }

  @Test
  void deserializesTheNoFundEnvelopeWithoutData() throws Exception {
    JavaType type =
        json.getTypeFactory()
            .constructParametricType(ApimResponseEnvelope.class, TrpGetFundFactSheetApimDataItem.class);

    ApimResponseEnvelope<TrpGetFundFactSheetApimDataItem> envelope =
        json.readValue("{\"response\":{\"err-message\":\"err.tpa.no.fund.fact.sheet\"}}", type);

    assertThat(envelope.getResponse().getErrMessage()).isEqualTo("err.tpa.no.fund.fact.sheet");
    assertThat(envelope.getResponse().getData()).isNull();
  }

  @Test
  void missingAndNullSeqStayNull() throws Exception {
    var missing = json.readValue("{\"name-eng\":\"Fund A\"}", TrpGetFundFactSheetApimRow.class);
    var explicitNull = json.readValue("{\"seq\":null,\"name-eng\":\"Fund A\"}", TrpGetFundFactSheetApimRow.class);

    assertThat(missing.seq()).isNull();
    assertThat(explicitNull.seq()).isNull();
  }

  @Test
  void missingNamesAndLinksStayNull() throws Exception {
    var row = json.readValue("{\"seq\":1}", TrpGetFundFactSheetApimRow.class);

    assertThat(row.nameEng()).isNull();
    assertThat(row.nameChi()).isNull();
    assertThat(row.linkEng()).isNull();
    assertThat(row.linkChi()).isNull();
  }

  @Test
  void unknownPropertiesAreIgnoredAtItemAndRowLevel() throws Exception {
    var item =
        json.readValue(
            "{\"extra\":\"x\",\"FundFactSheet\":[{\"seq\":1,\"unknown\":true,\"name-eng\":\"Fund A\"}]}",
            TrpGetFundFactSheetApimDataItem.class);

    assertThat(item.fundFactSheet()).hasSize(1);
    assertThat(item.fundFactSheet().get(0).nameEng()).isEqualTo("Fund A");
  }

  @Test
  void aMissingFundFactSheetListStaysNull() throws Exception {
    var item = json.readValue("{}", TrpGetFundFactSheetApimDataItem.class);

    assertThat(item.fundFactSheet()).isNull();
  }
}

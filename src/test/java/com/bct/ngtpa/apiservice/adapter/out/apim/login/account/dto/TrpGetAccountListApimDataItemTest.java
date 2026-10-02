package com.bct.ngtpa.apiservice.adapter.out.apim.login.account.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TrpGetAccountListApimDataItemTest {

  private static final String SAMPLE =
      """
      {"env":"JP","trust-code":"JPM","sch-type":"OE","sch-name":"JP MORGAN CHASE - JMCI",
       "chin-sch-name":"","policy-no":"00000000118","cert-no":70,"term-status":"",
       "term-complete-date":""}
      """;

  @Test
  void deserializesNewSchemeNameFields() throws Exception {
    var item = new ObjectMapper().readValue(SAMPLE, TrpGetAccountListApimDataItem.class);
    assertThat(item.schemeName()).isEqualTo("JP MORGAN CHASE - JMCI");
    assertThat(item.schemeNameChi()).isEmpty();
  }

  @Test
  void deserializesPopulatedChineseSchemeName() throws Exception {
    String json =
        """
        {"env":"JP","trust-code":"JPM","sch-name":"JP MORGAN CHASE","chin-sch-name":"摩根大通"}
        """;
    var item = new ObjectMapper().readValue(json, TrpGetAccountListApimDataItem.class);
    assertThat(item.schemeNameChi()).isEqualTo("摩根大通");
  }

  @Test
  void compatibilityConstructorDefaultsNewFieldsToNull() {
    var item = new TrpGetAccountListApimDataItem("DB", "DBA", "8802", "OE", 1, "", "");
    assertThat(item.schemeName()).isNull();
    assertThat(item.schemeNameChi()).isNull();
    assertThat(item.accountEnv()).isEqualTo("DB");
    assertThat(item.certNo()).isEqualTo(1);
  }

  @Test
  void canonicalConstructorAcceptsSchemeNameFields() {
    var item =
        new TrpGetAccountListApimDataItem(
            "DB", "DBA", "8802", "OE", "JP MORGAN CHASE", "摩根大通", 1, "", "");
    assertThat(item.schemeName()).isEqualTo("JP MORGAN CHASE");
    assertThat(item.schemeNameChi()).isEqualTo("摩根大通");
  }

  @Test
  void toStringNeverDisclosesSchemeNameValues() {
    var item =
        new TrpGetAccountListApimDataItem(
            "DB", "DBA", "8802", "OE", "JP MORGAN CHASE", "摩根大通", 1, "T", "20/07/2026");
    assertThat(item.toString()).doesNotContain("JP MORGAN CHASE", "摩根大通");
  }

  @Test
  void ignoresUnknownResponseFields() throws Exception {
    String json = """
        {"env":"OI","trust-code":"JPM","future-field":"future"}
        """;
    var item = new ObjectMapper().readValue(json, TrpGetAccountListApimDataItem.class);
    assertThat(item.accountEnv()).isEqualTo("OI");
  }
}

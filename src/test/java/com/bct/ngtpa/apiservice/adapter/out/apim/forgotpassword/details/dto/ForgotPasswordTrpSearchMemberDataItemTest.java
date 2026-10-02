package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ForgotPasswordTrpSearchMemberDataItemTest {

  private static final String SAMPLE =
      """
      {"env":"JP","trust-code":"JPM","sch-type":"OE","sch-name":"JP MORGAN CHASE - JMCI",
       "chin-sch-name":"","policy-no":"00000000118","cert-no":"70","term-status":"",
       "term-complete-date":"","isRegistered":true,"cust-id":"nick"}
      """;

  @Test
  void deserializesNewSchemeNameFields() throws Exception {
    var item = new ObjectMapper().readValue(SAMPLE, ForgotPasswordTrpSearchMemberDataItem.class);
    assertThat(item.schemeName()).isEqualTo("JP MORGAN CHASE - JMCI");
    assertThat(item.schemeNameChi()).isEmpty();
  }

  @Test
  void compatibilityConstructorDefaultsNewFieldsToNull() {
    var item =
        new ForgotPasswordTrpSearchMemberDataItem(
            "TB", "MSIG", "OE", "0001", "2", "", "", false, null);
    assertThat(item.schemeName()).isNull();
    assertThat(item.schemeNameChi()).isNull();
    assertThat(item.env()).isEqualTo("TB");
  }

  @Test
  void canonicalConstructorAcceptsSchemeNameFields() {
    var item =
        new ForgotPasswordTrpSearchMemberDataItem(
            "JP", "JPM", "OE", "JP MORGAN CHASE", "摩根大通", "0001", "2", "", "", true, "nick");
    assertThat(item.schemeName()).isEqualTo("JP MORGAN CHASE");
    assertThat(item.schemeNameChi()).isEqualTo("摩根大通");
  }

  @Test
  void toStringNeverDisclosesSchemeNameValues() {
    var item =
        new ForgotPasswordTrpSearchMemberDataItem(
            "JP", "JPM", "OE", "JP MORGAN CHASE", "摩根大通", "0001", "2", "", "", true, "nick");
    assertThat(item.toString()).doesNotContain("JP MORGAN CHASE", "摩根大通", "nick");
  }
}

package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TrpSearchMemberApimDataItemTest {

  private static final String SAMPLE =
      """
            {"env":"DB","trust-code":"DBA","sch-type":"OE","policy-no":"00000008802",
             "cert-no":"5","term-status":"","term-complete-date":"","isRegistered":true,"cust-id":"db02"}
            """;

  @Test
  void deserializesRawApimFieldNames() throws Exception {
    var item = new ObjectMapper().readValue(SAMPLE, TrpSearchMemberApimDataItem.class);

    assertThat(item.env()).isEqualTo("DB");
    assertThat(item.trustCode()).isEqualTo("DBA");
    assertThat(item.schemeType()).isEqualTo("OE");
    assertThat(item.policyNo()).isEqualTo("00000008802");
    assertThat(item.certNo()).isEqualTo("5");
    assertThat(item.termStatus()).isEmpty();
    assertThat(item.termCompleteDate()).isEmpty();
    assertThat(item.isRegistered()).isTrue();
    assertThat(item.custId()).isEqualTo("db02");
  }

  @Test
  void ignoresUnknownResponseFields() throws Exception {
    String json =
        """
                {"env":"OI","trust-code":"JPM","future-field":"future","isRegistered":false}
                """;

    var item = new ObjectMapper().readValue(json, TrpSearchMemberApimDataItem.class);

    assertThat(item.env()).isEqualTo("OI");
    assertThat(item.trustCode()).isEqualTo("JPM");
    assertThat(item.isRegistered()).isFalse();
  }

  @Test
  void registrationFlagMayBeAbsentAndIsRepresentedAsNull() throws Exception {
    String json =
        """
                {"env":"OI","trust-code":"JPM"}
                """;

    var item = new ObjectMapper().readValue(json, TrpSearchMemberApimDataItem.class);

    assertThat(item.isRegistered()).isNull();
  }

  @Test
  void preservesIdentifierNumbersAsTransportStrings() throws Exception {
    String json =
        """
                {"policy-no":8802,"cert-no":5}
                """;

    var item = new ObjectMapper().readValue(json, TrpSearchMemberApimDataItem.class);

    assertThat(item.policyNo()).isEqualTo("8802");
    assertThat(item.certNo()).isEqualTo("5");
  }

  @Test
  void toStringNeverDisclosesSensitiveValues() {
    var item =
        new TrpSearchMemberApimDataItem(
            "DB", "DBA", "OE", "00000008802", "5", "", "15/07/1969", true, "db02");

    assertThat(item.toString())
        .doesNotContain("DB", "DBA", "OE", "00000008802", "5", "15/07/1969", "db02");
  }
}

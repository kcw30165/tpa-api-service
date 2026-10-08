package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto.ForgotPasswordTrpSearchMemberDataItem;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Confirms trustCode/schemeName/schemeNameChi (added for Stage II-4, see
 * docs/design/10-template-service/template-service-implementation-plan.md Appendix E.3 and E.9,
 * II-4.b0) are threaded from the matched APIM item into the lookup result.
 */
class ForgotPasswordTrpSearchMemberMapperTrustSchemeTest {

  private final ForgotPasswordTrpSearchMemberMapper mapper =
      new ForgotPasswordTrpSearchMemberMapper();

  @Test
  void matchedResultCarriesTrustCodeAndSchemeNameFields() {
    var item =
        new ForgotPasswordTrpSearchMemberDataItem(
            "DB",
            "DBA",
            "TYPE1",
            "JP MORGAN CHASE",
            "摩根大通",
            null,
            null,
            null,
            null,
            Boolean.TRUE,
            "CUST1");

    var result = mapper.toResult(null, List.of(item), "CUST1");

    assertThat(result.status()).isEqualTo(ForgotPasswordDetailsLookupStatus.MATCHED);
    assertThat(result.accountEnv()).isEqualTo("DB");
    assertThat(result.trustCode()).isEqualTo("DBA");
    assertThat(result.schemeName()).isEqualTo("JP MORGAN CHASE");
    assertThat(result.schemeNameChi()).isEqualTo("摩根大通");
  }

  @Test
  void matchedResultHandlesBlankSchemeNameFieldsAsNull() {
    var item =
        new ForgotPasswordTrpSearchMemberDataItem(
            "DB", "DBA", "TYPE1", "", "", null, null, null, null, Boolean.TRUE, "CUST1");

    var result = mapper.toResult(null, List.of(item), "CUST1");

    assertThat(result.status()).isEqualTo(ForgotPasswordDetailsLookupStatus.MATCHED);
    assertThat(result.schemeName()).isNull();
    assertThat(result.schemeNameChi()).isNull();
  }
}

package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCandidate;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Confirms schemeName/schemeNameChi (sch-name/chin-sch-name) actually flow from the APIM DTO
 * through to the application-layer candidate, per
 * docs/design/10-template-service/template-service-solution-design.md section 8.2. Added
 * alongside the pre-existing TrpSearchMemberApimResultMapperTest, which this does not modify.
 */
class TrpSearchMemberApimResultMapperSchemeNamePlumbingTest {

  private final TrpSearchMemberApimResultMapper mapper = new TrpSearchMemberApimResultMapper();

  @Test
  void mapsSchemeNameAndSchemeNameChiOntoMatchedCandidate() {
    TrpSearchMemberApimDataItem item =
        new TrpSearchMemberApimDataItem(
            "JP", "JPM", "OE", "JP MORGAN CHASE - JMCI", "摩根大通", "0001", "2", "", "", true, "user-1");

    UsernameRecoveryLookupResult result = mapper.toResult(null, List.of(item));

    assertThat(result.status()).isEqualTo(UsernameRecoveryLookupResult.Status.MATCHED);
    UsernameRecoveryLookupCandidate candidate = result.candidates().get(0);
    assertThat(candidate.schemeName()).isEqualTo("JP MORGAN CHASE - JMCI");
    assertThat(candidate.schemeNameChi()).isEqualTo("摩根大通");
  }

  @Test
  void mapsBlankSchemeNameChiToNullNotEmptyString() {
    TrpSearchMemberApimDataItem item =
        new TrpSearchMemberApimDataItem(
            "JP", "JPM", "OE", "JP MORGAN CHASE - JMCI", "", "0001", "2", "", "", true, "user-1");

    UsernameRecoveryLookupResult result = mapper.toResult(null, List.of(item));

    UsernameRecoveryLookupCandidate candidate = result.candidates().get(0);
    assertThat(candidate.schemeName()).isEqualTo("JP MORGAN CHASE - JMCI");
    assertThat(candidate.schemeNameChi()).isNull();
  }

  @Test
  void mapsAbsentSchemeNameFieldsToNull() {
    TrpSearchMemberApimDataItem item =
        new TrpSearchMemberApimDataItem("DB", "DBA", "OE", "0001", "2", "", "", true, "user-1");

    UsernameRecoveryLookupResult result = mapper.toResult(null, List.of(item));

    UsernameRecoveryLookupCandidate candidate = result.candidates().get(0);
    assertThat(candidate.schemeName()).isNull();
    assertThat(candidate.schemeNameChi()).isNull();
  }

  @Test
  void unregisteredCandidateWithSchemeNameStillMapsSchemeNameFields() {
    TrpSearchMemberApimDataItem item =
        new TrpSearchMemberApimDataItem(
            "DB", "DBB", "OE", "JP MORGAN CHASE", "摩根大通", "0001", "2", "", "", false, null);

    UsernameRecoveryLookupResult result = mapper.toResult(null, List.of(item));

    UsernameRecoveryLookupCandidate candidate = result.candidates().get(0);
    assertThat(candidate.registered()).isFalse();
    assertThat(candidate.schemeName()).isEqualTo("JP MORGAN CHASE");
    assertThat(candidate.schemeNameChi()).isEqualTo("摩根大通");
  }
}

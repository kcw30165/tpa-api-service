package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpSearchMemberApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.mapper.TrpSearchMemberApimResultMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupDownstreamException;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationLookupStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrpSearchMemberApimResultMapperTest {
  private final TrpSearchMemberApimResultMapper mapper = new TrpSearchMemberApimResultMapper();

  @Test
  void validEmailDataIsEligible() {
    var result = mapper.toResult("", List.of(memberItem("OI", "JPM")), emailCommand());

    assertThat(result.status()).isEqualTo(RegistrationLookupStatus.ELIGIBLE);
    assertThat(result.accounts()).hasSize(1);
  }

  @Test
  void validMobileDataIsEligible() {
    var result = mapper.toResult("", List.of(memberItem("OI", "JPM")), mobileCommand());

    assertThat(result.status()).isEqualTo(RegistrationLookupStatus.ELIGIBLE);
  }

  @Test
  void memberNotFoundIsNotEligible() {
    var result = mapper.toResult("err.tpa.member.not.found", List.of(), emailCommand());

    assertThat(result.status()).isEqualTo(RegistrationLookupStatus.NOT_ELIGIBLE);
    assertThat(result.accounts()).isEmpty();
  }

  @Test
  void unexpectedErrorLeavesNormalResultPath() {
    assertThatThrownBy(() -> mapper.toResult("err.tpa.bad.request", List.of(), emailCommand()))
        .isInstanceOf(RegistrationLookupDownstreamException.class)
        .extracting("reason")
        .isEqualTo(RegistrationLookupDownstreamException.Reason.UNEXPECTED_ERROR);
  }

  @Test
  void emptySuccessfulDataIsContractFailure() {
    assertThatThrownBy(() -> mapper.toResult("", List.of(), emailCommand()))
        .isInstanceOf(RegistrationLookupDownstreamException.class);
  }

  @Test
  void missingEnvOrTrustCodeIsContractFailure() {
    assertThatThrownBy(() -> mapper.toResult("", List.of(memberItem("", "JPM")), emailCommand()))
        .isInstanceOf(RegistrationLookupDownstreamException.class);

    assertThatThrownBy(() -> mapper.toResult("", List.of(memberItem("OI", "")), emailCommand()))
        .isInstanceOf(RegistrationLookupDownstreamException.class);
  }

  private static RegistrationLookupCommand emailCommand() {
    return new RegistrationLookupCommand(
        MemberIdentityType.ID,
        "A1234567",
        "01/01/1970",
        RegistrationContactType.EMAIL,
        "member@example.com",
        com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);
  }

  private static RegistrationLookupCommand mobileCommand() {
    return new RegistrationLookupCommand(
        MemberIdentityType.ID,
        "A1234567",
        "01/01/1970",
        RegistrationContactType.MOBILE,
        "91234567",
        com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR);
  }

  private static TrpSearchMemberApimDataItem memberItem(String env, String trustCode) {
    return new TrpSearchMemberApimDataItem(
        env, trustCode, "OE", "00000008802", "5", "", "30/06/2022", false, "");
  }
}

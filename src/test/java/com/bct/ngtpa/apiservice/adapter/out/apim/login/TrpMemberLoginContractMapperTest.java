package com.bct.ngtpa.apiservice.adapter.out.apim.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.login.dto.TrpMemberLoginApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.dto.TrpMemberLoginApimOtpItem;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrpMemberLoginContractMapperTest {

  private final TrpMemberLoginContractMapper mapper = new TrpMemberLoginContractMapper();

  @Test
  void requestUsesOnlyCustIdAndExactPassword() {
    var request =
        mapper.toRequest(
            new CredentialAuthenticationCommand(
                "normalized.member",
                "  Exact Password  ",
                "en",
                "req-1",
                com.bct.ngtpa.apiservice.application.dto.ActorUserRole.ROLE_USER_MBR));

    assertThat(request.custId()).isEqualTo("normalized.member");
    assertThat(request.password()).isEqualTo("  Exact Password  ");
    assertThat(request.toString()).doesNotContain("normalized.member", "Exact Password");
  }

  @Test
  void successfulItemMapsConfirmedContextAndRawProtectedDestinations() {
    var item =
        new TrpMemberLoginApimDataItem(
            true,
            "role_user_mbr",
            0,
            List.of(
                new TrpMemberLoginApimOtpItem("email", "member@example.com"),
                new TrpMemberLoginApimOtpItem("mobile", "91234567")),
            null,
            null,
            null);

    var result = mapper.toAuthenticated("normalized.member", item);

    assertThat(result.status()).isEqualTo(CredentialAuthenticationStatus.AUTHENTICATED);
    assertThat(result.normalizedUsername()).isEqualTo("normalized.member");
    assertThat(result.userRole().toLowerCase()).isEqualTo("role_user_mbr");
    assertThat(result.failCount()).isZero();
    assertThat(result.destinations())
        .extracting(destination -> destination.channel())
        .containsExactly("email", "mobile");
    assertThat(result.destinations())
        .extracting(destination -> destination.destination())
        .containsExactly("member@example.com", "91234567");
    assertThat(result.toString())
        .doesNotContain("normalized.member", "member@example.com", "91234567");
  }

  @Test
  void successfulItemCarriesTrustCodeAndSchemeNamesWhenPresent() {
    var item =
        new TrpMemberLoginApimDataItem(
            true,
            "role_user_mbr",
            0,
            List.of(new TrpMemberLoginApimOtpItem("email", "member@example.com")),
            "DBA",
            "Example Scheme",
            "示例計劃");

    var result = mapper.toAuthenticated("normalized.member", item);

    assertThat(result.trustCode()).isEqualTo("DBA");
    assertThat(result.schemeName()).isEqualTo("Example Scheme");
    assertThat(result.schemeNameChi()).isEqualTo("示例計劃");
    assertThat(result.toString()).doesNotContain("DBA", "Example Scheme", "示例計劃");
  }

  @Test
  void successfulItemWithoutTrustOrSchemeLeavesThemNull() {
    var item =
        new TrpMemberLoginApimDataItem(
            true,
            "role_user_mbr",
            0,
            List.of(new TrpMemberLoginApimOtpItem("email", "member@example.com")),
            null,
            null,
            null);

    var result = mapper.toAuthenticated("normalized.member", item);

    assertThat(result.trustCode()).isNull();
    assertThat(result.schemeName()).isNull();
    assertThat(result.schemeNameChi()).isNull();
  }

  @Test
  void rejectsUnsupportedDuplicateOrBlankDestinations() {
    assertThatThrownBy(
            () ->
                mapper.toAuthenticated(
                    "member",
                    new TrpMemberLoginApimDataItem(
                        true, null, 0, List.of(new TrpMemberLoginApimOtpItem("sms", "91234567")), null, null, null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("channel");

    assertThatThrownBy(
            () ->
                mapper.toAuthenticated(
                    "member",
                    new TrpMemberLoginApimDataItem(
                        true,
                        null,
                        0,
                        List.of(
                            new TrpMemberLoginApimOtpItem("email", "member@example.com"),
                            new TrpMemberLoginApimOtpItem("email", "member@example.com")),
                        null,
                        null,
                        null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("duplicate");

    assertThatThrownBy(
            () ->
                mapper.toAuthenticated(
                    "member",
                    new TrpMemberLoginApimDataItem(
                        true, null, 0, List.of(new TrpMemberLoginApimOtpItem("email", " ")), null, null, null)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("destination");
  }
}

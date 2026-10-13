package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto.ForgotPasswordTrpSearchMemberDataItem;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsDownstreamException;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForgotPasswordTrpSearchMemberMapperTest {
  private final ForgotPasswordTrpSearchMemberMapper mapper =
      new ForgotPasswordTrpSearchMemberMapper();

  @Test
  void requestUsesIdentityAndSubmittedDestinationWithoutCustomerId() {
    var request = mapper.toRequest(command("A@B.COM", null));
    assertThat(request.searchBy()).isEqualTo("STAFFNO");
    assertThat(request.searchValue()).isEqualTo("IDENTITY");
    assertThat(request.dateOfBirth()).isEqualTo("11/09/1974");
    assertThat(request.email()).isEqualTo("A@B.COM");
    assertThat(request.mobileNumber()).isEmpty();
    assertThat(request.getClass().getRecordComponents())
        .extracting(component -> component.getName())
        .doesNotContain("customerId", "custId");
  }

  @Test
  void anyRegisteredRecordWithMatchingCustomerIdContinues() {
    var result =
        mapper.toResult(
            "", List.of(item("TB", "MSIG", false, null), item("JP", "JPM", true, "nick")), "nick");
    assertThat(result.status()).isEqualTo(ForgotPasswordDetailsLookupStatus.MATCHED);
    assertThat(result.trustCode()).isEqualTo("JPM");
  }

  @Test
  void registeredUsernameMatchUsesConfiguredCaseInsensitiveSemantics() {
    var result =
        mapper.toResult(
            "",
            List.of(
                item("JP", "JPM", true, "JPJPM01"),
                item("TB", "HKBU", true, "TBHKBU01"),
                item("TB", "CITI", true, "TBCITI01")),
            "tbciti01");

    assertThat(result.status()).isEqualTo(ForgotPasswordDetailsLookupStatus.MATCHED);
    assertThat(result.trustCode()).isEqualTo("CITI");
  }

  @Test
  void matchingCustomerIdOnUnregisteredRecordDoesNotContinue() {
    var result = mapper.toResult("", List.of(item("TB", "MSIG", false, null)), "nick");
    assertThat(result.status()).isEqualTo(ForgotPasswordDetailsLookupStatus.NOT_FOUND);
  }

  @Test
  void registeredDifferentCustomerIdDoesNotContinue() {
    var result = mapper.toResult("", List.of(item("TB", "MSIG", true, "other")), "nick");
    assertThat(result.status()).isEqualTo(ForgotPasswordDetailsLookupStatus.NOT_FOUND);
  }

  @Test
  void malformedRegisteredRecordFailsClosed() {
    assertThatThrownBy(() -> mapper.toResult("", List.of(item("TB", "MSIG", true, null)), "nick"))
        .isInstanceOf(ForgotPasswordDetailsDownstreamException.class);
  }

  @Test
  void emptyDataIsBusinessNotFound() {
    assertThat(mapper.toResult("", List.of(), "nick").status())
        .isEqualTo(ForgotPasswordDetailsLookupStatus.NOT_FOUND);
  }

  private static ForgotPasswordDetailsLookupCommand command(String email, String mobile) {
    return new ForgotPasswordDetailsLookupCommand(
        "STAFF_NO",
        "IDENTITY",
        "11/09/1974",
        email,
        mobile,
        "nick",
        "request-id",
        ActorUserRole.ROLE_USER_MBR);
  }

  private static ForgotPasswordTrpSearchMemberDataItem item(
      String env, String trustCode, boolean registered, String customerId) {
    return new ForgotPasswordTrpSearchMemberDataItem(
        env, trustCode, "OE", null, null, "0001", "2", "", "", registered, customerId);
  }
}

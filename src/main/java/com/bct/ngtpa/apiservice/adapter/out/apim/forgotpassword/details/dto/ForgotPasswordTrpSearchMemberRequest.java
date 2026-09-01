package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.details.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/** Forgot Password-owned TRPSearchMember request. Sensitive values are never logged. */
public record ForgotPasswordTrpSearchMemberRequest(
    @JsonProperty("search-by") String searchBy,
    @JsonProperty("search-value") String searchValue,
    @JsonProperty("dob") String dateOfBirth,
    @JsonProperty("email") String email,
    @JsonProperty("mobile-number") String mobileNumber) {
  public ForgotPasswordTrpSearchMemberRequest {
    Objects.requireNonNull(searchBy, "searchBy must not be null");
    Objects.requireNonNull(searchValue, "searchValue must not be null");
    Objects.requireNonNull(dateOfBirth, "dateOfBirth must not be null");
    email = email == null ? "" : email;
    mobileNumber = mobileNumber == null ? "" : mobileNumber;
  }

  @Override
  public String toString() {
    return "ForgotPasswordTrpSearchMemberRequest[searchBy="
        + searchBy
        + ", searchValue=, dateOfBirth=, email=, mobileNumber=]";
  }
}

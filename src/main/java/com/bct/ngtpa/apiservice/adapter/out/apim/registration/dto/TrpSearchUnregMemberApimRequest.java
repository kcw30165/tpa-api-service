package com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public record TrpSearchUnregMemberApimRequest(
    @JsonProperty("search-by") String searchBy,
    @JsonProperty("search-value") String searchValue,
    @JsonProperty("dob") String dob,
    @JsonProperty("email") String email,
    @JsonProperty("mobile-number") String mobileNumber) {
  public TrpSearchUnregMemberApimRequest {
    Objects.requireNonNull(searchBy, "searchBy must not be null");
    Objects.requireNonNull(searchValue, "searchValue must not be null");
    Objects.requireNonNull(dob, "dob must not be null");
    email = email == null ? "" : email;
    mobileNumber = mobileNumber == null ? "" : mobileNumber;
  }

  @Override
  public String toString() {
    return "TrpSearchUnregMemberApimRequest[searchBy="
        + searchBy
        + ", searchValue=<redacted>, dob=<redacted>, email=<redacted>, mobileNumber=<redacted>]";
  }
}

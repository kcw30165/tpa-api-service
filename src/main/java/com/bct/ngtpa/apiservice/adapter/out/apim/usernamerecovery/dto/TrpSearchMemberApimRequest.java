package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Adapter-only TRPSearchMember request body.
 *
 * <p>Raw APIM field names stay inside the APIM boundary: {@code search-by}, {@code search-value},
 * {@code dob}, {@code email}, {@code mobile-number}. Exactly one contact is populated by the
 * mapper; the unused field is sent as an empty string because APIM requires the field. {@link
 * #toString()} never discloses identity, DOB, or contact values.
 */
public record TrpSearchMemberApimRequest(
    @JsonProperty("search-by") String searchBy,
    @JsonProperty("search-value") String searchValue,
    @JsonProperty("dob") String dob,
    @JsonProperty("email") String email,
    @JsonProperty("mobile-number") String mobileNumber) {

  public TrpSearchMemberApimRequest {
    Objects.requireNonNull(searchBy, "searchBy must not be null");
    Objects.requireNonNull(searchValue, "searchValue must not be null");
    Objects.requireNonNull(dob, "dob must not be null");
    email = email == null ? "" : email;
    mobileNumber = mobileNumber == null ? "" : mobileNumber;
  }

  @Override
  public String toString() {
    return "TrpSearchMemberApimRequest[searchBy="
        + searchBy
        + ", searchValue=<redacted>, dob=<redacted>, email=<redacted>, mobileNumber=<redacted>]";
  }
}

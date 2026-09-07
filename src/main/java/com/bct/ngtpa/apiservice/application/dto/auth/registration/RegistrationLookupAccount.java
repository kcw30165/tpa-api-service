package com.bct.ngtpa.apiservice.application.dto.auth.registration;

/** Complete TRPSearchMember item retained for registration FE representation. */
public record RegistrationLookupAccount(
    String accountEnv,
    String trustCode,
    String schemeType,
    String policyNo,
    String certNo,
    String termStatus,
    String termCompleteDate,
    Boolean registered,
    String customerId) {
  public RegistrationLookupAccount(
      String email, String mobileNumber, String accountEnv, String trustCode) {
    this(accountEnv, trustCode, null, null, null, null, null, Boolean.FALSE, null);
  }

  @Override
  public String toString() {
    return "RegistrationLookupAccount[accountEnv=<redacted>, trustCode=<redacted>, schemeType=<redacted>, policyNo=<redacted>, certNo=<redacted>, termStatus=<redacted>, termCompleteDate=<redacted>, registered="
        + registered
        + ", customerId=<redacted>]";
  }
}

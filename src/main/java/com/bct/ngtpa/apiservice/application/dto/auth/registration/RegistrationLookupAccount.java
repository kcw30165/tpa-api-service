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
  @Override
  public String toString() {
    return "RegistrationLookupAccount[accountEnv=<redacted>, trustCode=<redacted>, schemeType=<redacted>, policyNo=<redacted>, certNo=<redacted>, termStatus=<redacted>, termCompleteDate=<redacted>, registered="
        + registered
        + ", customerId=<redacted>]";
  }
}

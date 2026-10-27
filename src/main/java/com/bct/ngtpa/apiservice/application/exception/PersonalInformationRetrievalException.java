package com.bct.ngtpa.apiservice.application.exception;

/**
 * Signals that the field-access config of the selected account could not be retrieved from
 * TRPGetMemberInfo for the personal-information PUT (decision O-1). Every failure of that lookup
 * maps to this one exception so that no downstream detail can leak into a public response. The
 * exception handler turns it into HTTP 503 SYSTEM_ERROR with {@code
 * err.personal-information.retrieval.unavailable}.
 */
public final class PersonalInformationRetrievalException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private PersonalInformationRetrievalException() {
    super("Personal information retrieval failed");
  }

  public static PersonalInformationRetrievalException systemFailure() {
    return new PersonalInformationRetrievalException();
  }
}

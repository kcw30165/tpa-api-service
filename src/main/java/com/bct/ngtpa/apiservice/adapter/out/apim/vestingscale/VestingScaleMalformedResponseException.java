package com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale;

final class VestingScaleMalformedResponseException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String malformedField;
  private final MalformedReason malformedReason;

  VestingScaleMalformedResponseException() {
    this(null, null);
  }

  VestingScaleMalformedResponseException(String malformedField, MalformedReason malformedReason) {
    super("Malformed vesting-scale APIM response");
    this.malformedField = malformedField;
    this.malformedReason = malformedReason;
  }

  String malformedField() {
    return malformedField;
  }

  MalformedReason malformedReason() {
    return malformedReason;
  }

  enum MalformedReason {
    INVALID_DATE_FORMAT,
    REQUIRED_TEXT_TYPE,
    REQUIRED_TEXT_BLANK
  }
}

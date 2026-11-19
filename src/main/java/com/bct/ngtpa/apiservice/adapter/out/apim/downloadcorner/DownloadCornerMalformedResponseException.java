package com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner;

final class DownloadCornerMalformedResponseException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String malformedField;
  private final MalformedReason malformedReason;

  DownloadCornerMalformedResponseException(String malformedField, MalformedReason malformedReason) {
    super("Malformed Download Corner APIM response");
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
    MISSING_REQUIRED_FIELD,
    INVALID_TYPE,
    INVALID_CARDINALITY,
    REQUIRED_TEXT_BLANK
  }
}

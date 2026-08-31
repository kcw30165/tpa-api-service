package com.bct.ngtpa.apiservice.application.exception;

public final class MissingRequiredGeneralMetadataException extends RuntimeException {
  public MissingRequiredGeneralMetadataException() {
    super("Required general metadata is unavailable");
  }
}

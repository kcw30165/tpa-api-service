package com.bct.ngtpa.apiservice.application.usecase.metadata;

public final class GeneralMetadataConcurrencyException extends RuntimeException {
  public GeneralMetadataConcurrencyException() {
    super("Concurrent general metadata update could not be resolved");
  }
}

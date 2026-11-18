package com.bct.ngtpa.apiservice.application.exception;

public final class DownloadCornerRetrievalException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private DownloadCornerRetrievalException() {
    super("Download Corner retrieval failed");
  }

  public static DownloadCornerRetrievalException systemFailure() {
    return new DownloadCornerRetrievalException();
  }
}

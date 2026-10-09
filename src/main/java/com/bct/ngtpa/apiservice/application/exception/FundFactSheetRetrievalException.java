package com.bct.ngtpa.apiservice.application.exception;

/**
 * Signals that the fund fact sheet could not be retrieved. Every downstream rejection, malformed
 * response and transport failure maps to this one exception so that no downstream detail can leak
 * into a public response.
 */
public final class FundFactSheetRetrievalException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private FundFactSheetRetrievalException() {
    super("Fund fact sheet retrieval failed");
  }

  public static FundFactSheetRetrievalException systemFailure() {
    return new FundFactSheetRetrievalException();
  }
}

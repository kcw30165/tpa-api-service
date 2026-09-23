package com.bct.ngtpa.apiservice.application.exception;

/**
 * Single generic Fund Price retrieval failure.
 *
 * <p>Per docs/design/9-fund-price/fund-price-error-matrix.md, every nonblank downstream {@code
 * err-message} (including {@code err.tpa.invalid.date} and {@code err.tpa.no.fund.price}), every
 * invalid downstream contract (blank/unsupported mode or fund-price-type, non-positive maximum
 * months, unparseable/blank available dates, wrong item count), and every transport/deserialization
 * failure maps to the same public HTTP 503 SYSTEM_ERROR. Raw downstream or exception text is never
 * exposed -- the message on this exception is deliberately generic.
 */
public final class FundPriceRetrievalException extends RuntimeException {

  private FundPriceRetrievalException(String message) {
    super(message);
  }

  public static FundPriceRetrievalException systemFailure() {
    return new FundPriceRetrievalException("Fund Price retrieval failed");
  }
}

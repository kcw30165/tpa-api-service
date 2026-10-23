package com.bct.ngtpa.apiservice.adapter.in.web.error;

/**
 * Whether a public message should be resolved for one {@link ErrorEntry}.
 *
 * <p>{@code RESOLVE} means {@link PublicMessageResolver} calls its message-resolution logic for
 * this entry's error code. {@code NONE} means the message is always {@code null}, as with {@code
 * AccountBalanceRetrievalException}, {@code AccountGainLossRetrievalException} and {@code
 * FundFactSheetRetrievalException} (O-12, docs/design/15-exception-handling/06-open-items.md).
 */
public enum MessagePolicy {
  RESOLVE,
  NONE
}

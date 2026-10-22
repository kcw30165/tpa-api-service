package com.bct.ngtpa.apiservice.adapter.in.web.error;

/**
 * Sub-task 1.7 (M1 closure, Option A): whether the renderer should resolve a public message for one
 * {@link ErrorEntry}.
 *
 * <p>{@code RESOLVE} matches the behaviour every {@code delegate(...)}-routed exception and {@code
 * WebExchangeBindException} already have today: the renderer calls {@code
 * resolvePublicMessage(...)} for this entry's error code.
 *
 * <p>{@code NONE} matches the behaviour {@code AccountBalanceRetrievalException}, {@code
 * AccountGainLossRetrievalException} and {@code FundFactSheetRetrievalException} already have
 * today: the message is always {@code null}, even outside a pre-login request. This is the
 * null-message contract confirmed by sub-task 1.6b's characterization pack (O-12,
 * docs/design/15-exception-handling/06-open-items.md) and preserved verbatim by this sub-task.
 */
public enum MessagePolicy {
  RESOLVE,
  NONE
}

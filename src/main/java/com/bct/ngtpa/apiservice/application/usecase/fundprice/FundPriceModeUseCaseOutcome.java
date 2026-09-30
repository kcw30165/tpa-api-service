package com.bct.ngtpa.apiservice.application.usecase.fundprice;

/**
 * Generic outcome vocabulary for {@link FundPriceModeUseCase}.
 *
 * <p>Deliberately collapses every non-{@code APPLIED} {@link
 * com.bct.ngtpa.apiservice.application.usecase.session.authenticated.AuthenticatedSessionConcurrencyOutcome}
 * into a single {@link #SYSTEM_FAILURE}, mirroring {@link
 * com.bct.ngtpa.apiservice.application.exception.FundPriceRetrievalException}'s own
 * single-generic-failure design: the mode endpoint's public contract only ever surfaces one failure
 * shape (HTTP 503 SYSTEM_ERROR), per fund-price-error-matrix.md's session/contract error section.
 * There is no caller-visible distinction between "session not found", "session not usable", or
 * "revision conflict exhausted" at this layer.
 */
public enum FundPriceModeUseCaseOutcome {
  APPLIED,
  SYSTEM_FAILURE
}

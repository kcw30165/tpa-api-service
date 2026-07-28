package com.bct.ngtpa.apiservice.adapter.out.apim.retry;

/**
 * Classifies whether an APIM business operation may use transient transport
 * retry.
 */
public enum ApimRetryEligibility {
    SAFE_BUSINESS_CALL(true),
    IDEMPOTENT_MUTATION(true),
    NON_RETRYABLE(false);

    private final boolean retryEligible;

    ApimRetryEligibility(boolean retryEligible) {
        this.retryEligible = retryEligible;
    }

    public boolean retryEligible() {
        return retryEligible;
    }
}

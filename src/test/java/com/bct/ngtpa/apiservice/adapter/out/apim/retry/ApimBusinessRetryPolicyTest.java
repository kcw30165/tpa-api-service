package com.bct.ngtpa.apiservice.adapter.out.apim.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import org.junit.jupiter.api.Test;

class ApimBusinessRetryPolicyTest {

    @Test
    void retryEligibilityDistinguishesSafeIdempotentAndNonRetryableOperations() {
        assertThat(ApimRetryEligibility.values()).containsExactly(
                ApimRetryEligibility.SAFE_BUSINESS_CALL,
                ApimRetryEligibility.IDEMPOTENT_MUTATION,
                ApimRetryEligibility.NON_RETRYABLE);
        assertThat(ApimRetryEligibility.SAFE_BUSINESS_CALL.retryEligible()).isTrue();
        assertThat(ApimRetryEligibility.IDEMPOTENT_MUTATION.retryEligible()).isTrue();
        assertThat(ApimRetryEligibility.NON_RETRYABLE.retryEligible()).isFalse();
    }

    @Test
    void businessRetryConfigurationRequiresThreeTotalAttemptsAndNonNegativeBackoff() {
        ApimProperties properties = new ApimProperties();
        properties.getRetry().getBusinessCall().setMaxAttempts(3);
        properties.getRetry().getBusinessCall().setBackoffMilliseconds(100);
        properties.validateRetryPolicy();

        assertThat(properties.getRetry().getBusinessCall().getMaxAttempts()).isEqualTo(3);
        assertThat(properties.getRetry().getBusinessCall().getBackoffMilliseconds()).isEqualTo(100);
    }

    @Test
    void invalidRetryConfigurationFailsValidationWithoutAddingFacadeConstructors() {
        ApimProperties properties = new ApimProperties();
        properties.getRetry().getBusinessCall().setMaxAttempts(0);
        assertThatThrownBy(properties::validateRetryPolicy)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max-attempts");

        properties.getRetry().getBusinessCall().setMaxAttempts(3);
        properties.getRetry().getBusinessCall().setBackoffMilliseconds(-1);
        assertThatThrownBy(properties::validateRetryPolicy)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("backoff-milliseconds");
    }
}

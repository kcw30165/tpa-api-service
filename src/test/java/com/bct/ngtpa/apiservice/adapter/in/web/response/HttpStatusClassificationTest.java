package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Focused tests for the typed HTTP-status classification policy.
 *
 * <p>Nine normalized public {@link ApiStatus} values map to {@link HttpStatus};
 * the two unnormalized downstream statuses fail closed with
 * {@link IllegalArgumentException}; the typed override applies only after
 * downstream-status normalization is proven.
 */
class HttpStatusClassificationTest {

    @Test
    void shouldMapSuccessLikeStatusesToOk() {
        assertThat(HttpStatusClassification.classify(ApiStatus.SUCCESS)).isEqualTo(HttpStatus.OK);
        assertThat(HttpStatusClassification.classify(ApiStatus.UPDATED)).isEqualTo(HttpStatus.OK);
        assertThat(HttpStatusClassification.classify(ApiStatus.SUBMITTED)).isEqualTo(HttpStatus.OK);
        assertThat(HttpStatusClassification.classify(ApiStatus.NO_CHANGE)).isEqualTo(HttpStatus.OK);
        assertThat(HttpStatusClassification.classify(ApiStatus.PENDING_APPROVAL)).isEqualTo(HttpStatus.OK);
        assertThat(HttpStatusClassification.classify(ApiStatus.PARTIAL_SUCCESS)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldMapValidationFailedToBadRequest() {
        assertThat(HttpStatusClassification.classify(ApiStatus.VALIDATION_FAILED)).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldMapBusinessRejectedToUnprocessableEntity() {
        assertThat(HttpStatusClassification.classify(ApiStatus.BUSINESS_REJECTED))
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void shouldMapSystemErrorToServiceUnavailable() {
        assertThat(HttpStatusClassification.classify(ApiStatus.SYSTEM_ERROR)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldRejectDownstreamRejectedAsUnnormalized() {
        assertThatThrownBy(() -> HttpStatusClassification.classify(ApiStatus.DOWNSTREAM_REJECTED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectDownstreamErrorAsUnnormalized() {
        assertThatThrownBy(() -> HttpStatusClassification.classify(ApiStatus.DOWNSTREAM_ERROR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullStatus() {
        assertThatThrownBy(() -> HttpStatusClassification.classify(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldApplyConflictOverrideToBusinessRejected() {
        assertThat(HttpStatusClassification.classify(
                ApiStatus.BUSINESS_REJECTED, HttpStatusClassificationOverride.CONFLICT))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void shouldApplyTooManyRequestsOverrideToBusinessRejected() {
        assertThat(HttpStatusClassification.classify(
                ApiStatus.BUSINESS_REJECTED, HttpStatusClassificationOverride.TOO_MANY_REQUESTS))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void shouldApplyForbiddenOverrideToValidationFailed() {
        assertThat(HttpStatusClassification.classify(
                ApiStatus.VALIDATION_FAILED, HttpStatusClassificationOverride.FORBIDDEN))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldApplyConflictOverrideToSystemError() {
        assertThat(HttpStatusClassification.classify(
                ApiStatus.SYSTEM_ERROR, HttpStatusClassificationOverride.CONFLICT))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void shouldRejectNullOverride() {
        assertThatThrownBy(() -> HttpStatusClassification.classify(ApiStatus.BUSINESS_REJECTED, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectDownstreamStatusesEvenWithOverride() {
        assertThatThrownBy(() -> HttpStatusClassification.classify(
                ApiStatus.DOWNSTREAM_REJECTED, HttpStatusClassificationOverride.CONFLICT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HttpStatusClassification.classify(
                ApiStatus.DOWNSTREAM_ERROR, HttpStatusClassificationOverride.CONFLICT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

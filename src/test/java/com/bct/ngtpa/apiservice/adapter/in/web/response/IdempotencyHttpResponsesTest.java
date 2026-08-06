package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.workflow.SharedWorkflowErrorKey;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import reactor.test.StepVerifier;

class IdempotencyHttpResponsesTest {

    @Test
    void shouldReturn400WhenKeyMissing() {
        StepVerifier.create(IdempotencyHttpResponses.<String>missingKey())
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(entity.getBody()).isNotNull();
                    assertThat(entity.getBody().success()).isFalse();
                    assertThat(entity.getBody().status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
                    assertThat(entity.getBody().result()).isNull();
                    assertThat(entity.getBody().messages()).isEmpty();
                    assertThat(entity.getBody().errors()).singleElement().satisfies(error -> {
                        assertThat(error.type()).isEqualTo("FORM");
                        assertThat(error.code()).isEqualTo(SharedWorkflowErrorKey.IDEMPOTENCY_MISSING.value());
                        assertThat(error.targets()).containsExactly("Idempotency-Key");
                        assertThat(error.source()).isEqualTo("SERVER");
                    });
                })
                .verifyComplete();
    }

    @Test
    void shouldReturn409WithRetryAfterWhenInProgress() {
        StepVerifier.create(IdempotencyHttpResponses.<String>inProgress(30))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(entity.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
                    assertThat(entity.getBody()).isNotNull();
                    assertThat(entity.getBody().success()).isFalse();
                    assertThat(entity.getBody().status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
                    assertThat(entity.getBody().result()).isNull();
                    assertThat(entity.getBody().messages()).isEmpty();
                    assertThat(entity.getBody().errors()).singleElement().satisfies(error -> {
                        assertThat(error.type()).isEqualTo("BUSINESS");
                        assertThat(error.code()).isEqualTo(SharedWorkflowErrorKey.IDEMPOTENCY_IN_PROGRESS.value());
                        assertThat(error.targets()).isEmpty();
                        assertThat(error.source()).isEqualTo("SERVER");
                    });
                })
                .verifyComplete();
    }

    @Test
    void shouldReturn409WhenKeyReused() {
        StepVerifier.create(IdempotencyHttpResponses.<String>keyReused())
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(entity.getBody()).isNotNull();
                    assertThat(entity.getBody().success()).isFalse();
                    assertThat(entity.getBody().status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
                    assertThat(entity.getBody().result()).isNull();
                    assertThat(entity.getBody().messages()).isEmpty();
                    assertThat(entity.getBody().errors()).singleElement().satisfies(error -> {
                        assertThat(error.type()).isEqualTo("FORM");
                        assertThat(error.code()).isEqualTo(SharedWorkflowErrorKey.IDEMPOTENCY_KEY_REUSED.value());
                        assertThat(error.targets()).containsExactly("Idempotency-Key");
                        assertThat(error.source()).isEqualTo("SERVER");
                    });
                })
                .verifyComplete();
    }

    @Test
    void shouldReplayCompletedResponse() {
        MutationResponse<String> original = MutationResponse.success(
                ApiStatus.SUCCESS, "completed", List.of());

        StepVerifier.create(IdempotencyHttpResponses.completedReplay(original))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(entity.getBody()).isSameAs(original);
                    assertThat(entity.getHeaders().isEmpty()).isTrue();
                })
                .verifyComplete();
    }
}

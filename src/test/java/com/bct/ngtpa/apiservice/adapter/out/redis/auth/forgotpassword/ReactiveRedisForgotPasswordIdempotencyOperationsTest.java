package com.bct.ngtpa.apiservice.adapter.out.redis.auth.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SuppressWarnings({"unchecked", "rawtypes"})
class ReactiveRedisForgotPasswordIdempotencyOperationsTest {
    private static final String RESEND_KEY =
            "ngtpa:auth:idempotency:forgot-password-otp-resend:idem_fp_123";
    private static final String HASH = "0".repeat(64);

    @Test
    void attachPendingCommitRefValidatesAgainstActionArgumentNotHardCodedAction() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        ReactiveRedisForgotPasswordIdempotencyOperations operations =
                new ReactiveRedisForgotPasswordIdempotencyOperations(redisTemplate);
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(List.class)))
                .thenReturn(Flux.just("stored-json"));

        StepVerifier.create(operations.attachPendingCommitRef(
                        RESEND_KEY,
                        "OTP_RESEND",
                        HASH,
                        "ref-resend-1",
                        Duration.ofSeconds(300)))
                .expectNext("stored-json")
                .verifyComplete();

        ArgumentCaptor<RedisScript> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<List> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List> argsCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), keysCaptor.capture(), argsCaptor.capture());

        String script = scriptCaptor.getValue().getScriptAsString();
        assertThat(script)
                .doesNotContain("DETAILS_AND_OTP")
                .contains("o.action")
                .contains("ARGV[");
        assertThat((List<String>) keysCaptor.getValue()).containsExactly(RESEND_KEY);
        assertThat((List<String>) argsCaptor.getValue()).containsExactly(
                "OTP_RESEND", HASH, "ref-resend-1", "300000");
    }
}

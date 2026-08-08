package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCsrfHash;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySession;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionRevision;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Group 1: minimal serialization round-trip and redaction.
 *
 * <p>Verifies the persisted JSON carries only Task 010 minimal protected fields,
 * round-trips the whole aggregate, never stores prohibited data, and redacts
 * sensitive values from {@code toString} and failure messages.
 */
class RedisUsernameRecoverySessionJsonMapperTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-08T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-08T00:10:00Z");

    private final RedisUsernameRecoverySessionJsonMapper mapper =
            new RedisUsernameRecoverySessionJsonMapper();

    @Test
    void roundTripPreservesWholeProtectedAggregate() {
        var session = activeSession();

        var restored = mapper.fromJson(mapper.toJson(session));

        assertThat(restored).isEqualTo(session);
    }

    @Test
    void requestedCandidateDeliveryMetadataRoundTrips() {
        var requested = sessionWithTwoRegisteredCandidates()
                .reserve(new UsernameRecoverySchemeRef("sch_opaque-1"))
                .markRequested(new UsernameRecoverySchemeRef("sch_opaque-1"), "sender-ref-1",
                        Instant.parse("2026-08-08T00:02:00Z"));

        var restored = mapper.fromJson(mapper.toJson(requested));

        assertThat(restored).isEqualTo(requested);
        assertThat(restored.status()).isEqualTo(UsernameRecoveryWorkflowStatus.PARTIALLY_REQUESTED);
    }

    @Test
    void storedJsonContainsOnlyTask010MinimalFields() {
        var json = mapper.toJson(activeSession());

        assertThat(json).contains(
                "sessionId", "scenario", "status", "revision", "csrfHash",
                "destination", "destinationChannel", "language",
                "createdAt", "expiresAt", "candidates",
                "schemeRef", "accountEnv", "trustCode", "registered",
                "userName", "deliveryState");
        assertThat(json)
                .doesNotContain("policyNo")
                .doesNotContain("certNo")
                .doesNotContain("schemeType")
                .doesNotContain("termination")
                .doesNotContain("dateOfBirth")
                .doesNotContain("dob")
                .doesNotContain("captchaAnswer")
                .doesNotContain("captchaText")
                .doesNotContain("rawCsrfToken")
                .doesNotContain("csrfToken\"")
                .doesNotContain("auth_flow_sid")
                .doesNotContain("cookie")
                .doesNotContain("http")
                .doesNotContain("apim")
                .doesNotContain("cust-id")
                .doesNotContain("password");
    }

    @Test
    void registeredUsernameIsPersistedOnlyForRegisteredCandidates() {
        var json = mapper.toJson(activeSession());

        assertThat(json).contains("\"userName\":\"user-sch\"");
        assertThat(json).contains("\"userName\":null");
    }

    @Test
    void recordToStringRedactsSensitiveValues() {
        var record = RedisUsernameRecoverySessionRecord.from(activeSession());

        assertThat(record.toString())
                .doesNotContain("urs_opaque-1")
                .doesNotContain("csrf_hash_opaque")
                .doesNotContain("member@example.com")
                .doesNotContain("user-sch")
                .doesNotContain("sch_opaque-1")
                .doesNotContain("sch_opaque-2");
    }

    @Test
    void malformedRedisJsonFailsSafelyWithoutDisclosingContent() {
        assertThatThrownBy(() -> mapper.fromJson("{not-json"))
                .isInstanceOf(CacheException.class)
                .hasMessage("Username recovery session state deserialization failed")
                .hasMessageNotContaining("{not-json");
    }

    private static UsernameRecoverySession sessionWithTwoRegisteredCandidates() {
        var registeredOne = new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef("sch_opaque-1"),
                        "HK",
                        "trust-1",
                        true,
                        "user-sch",
                        UsernameRecoveryDeliveryState.AVAILABLE),
                null,
                null);
        var registeredTwo = new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef("sch_opaque-2"),
                        "HK",
                        "trust-2",
                        true,
                        "user-sch-2",
                        UsernameRecoveryDeliveryState.AVAILABLE),
                null,
                null);
        return new UsernameRecoverySession(
                new UsernameRecoverySessionId("urs_opaque-1"),
                UsernameRecoverySession.Scenario.USERNAME_RECOVERY,
                UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoverySessionRevision(0),
                new UsernameRecoveryCsrfHash("csrf_hash_opaque"),
                new UsernameRecoveryVerifiedDestination("member@example.com",
                        UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                Locale.forLanguageTag("zh-HK"),
                CREATED_AT,
                EXPIRES_AT,
                List.of(registeredOne, registeredTwo));
    }

    private static UsernameRecoverySession activeSession() {
        var registered = new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef("sch_opaque-1"),
                        "HK",
                        "trust-1",
                        true,
                        "user-sch",
                        UsernameRecoveryDeliveryState.AVAILABLE),
                null,
                null);
        var unregistered = new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef("sch_opaque-2"),
                        "HK",
                        "trust-2",
                        false,
                        null,
                        UsernameRecoveryDeliveryState.AVAILABLE),
                null,
                null);
        return new UsernameRecoverySession(
                new UsernameRecoverySessionId("urs_opaque-1"),
                UsernameRecoverySession.Scenario.USERNAME_RECOVERY,
                UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoverySessionRevision(0),
                new UsernameRecoveryCsrfHash("csrf_hash_opaque"),
                new UsernameRecoveryVerifiedDestination("member@example.com",
                        UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                Locale.forLanguageTag("zh-HK"),
                CREATED_AT,
                EXPIRES_AT,
                List.of(registered, unregistered));
    }
}

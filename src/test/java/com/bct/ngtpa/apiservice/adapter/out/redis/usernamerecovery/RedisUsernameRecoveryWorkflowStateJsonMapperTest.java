package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowState;
import com.bct.ngtpa.apiservice.application.dto.session.usernamerecovery.UsernameRecoveryWorkflowStateRevision;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryDeliveryState;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryVerifiedDestination;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import com.bct.ngtpa.apiservice.exception.CacheException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Group 2: workflow-state JSON serialization boundary.
 *
 * <p>Verifies lossless round trips, protected-value handling in persisted JSON, safe
 * {@link CacheException} mapping for blank, malformed, and invariant-breaking input, rejection of
 * legacy session-shaped JSON, and safe exception messages that disclose no protected value.
 */
class RedisUsernameRecoveryWorkflowStateJsonMapperTest {

    private final RedisUsernameRecoveryWorkflowStateJsonMapper mapper =
            new RedisUsernameRecoveryWorkflowStateJsonMapper();

    @Test
    void losslessRoundTrip() {
        var state = activeWorkflowState();

        var restored = mapper.fromJson(mapper.toJson(state));

        assertThat(restored).isEqualTo(state);
    }

    @Test
    void registeredUsernameAndDeliverySettlementFieldsRemainProtectedInJsonPersistence() {
        var requested = activeWorkflowState()
                .reserve(new UsernameRecoverySchemeRef("sch_opaque-1"))
                .markRequested(new UsernameRecoverySchemeRef("sch_opaque-1"), "sender-ref-1",
                        Instant.parse("2026-08-08T00:02:00Z"));

        var json = mapper.toJson(requested);
        var restored = mapper.fromJson(json);

        assertThat(restored).isEqualTo(requested);
        assertThat(json)
                .contains("\"userName\":\"user-sch\"")
                .contains("\"userName\":null")
                .contains("\"requestedAt\":\"2026-08-08T00:02:00Z\"")
                .contains("\"senderReference\":\"sender-ref-1\"");
        assertThat(RedisUsernameRecoveryWorkflowStateRecord.from(requested).toString())
                .doesNotContain("sender-ref-1")
                .doesNotContain("user-sch")
                .doesNotContain("member@example.com")
                .doesNotContain("afs_abc123");
    }

    @Test
    void blankAndMalformedJsonMapToSafeCacheException() {
        assertThatThrownBy(() -> mapper.fromJson(null))
                .isInstanceOf(CacheException.class)
                .hasMessage("Username recovery workflow state deserialization failed");
        assertThatThrownBy(() -> mapper.fromJson("   "))
                .isInstanceOf(CacheException.class)
                .hasMessage("Username recovery workflow state deserialization failed");
        assertThatThrownBy(() -> mapper.fromJson("{not-json"))
                .isInstanceOf(CacheException.class)
                .hasMessage("Username recovery workflow state deserialization failed")
                .hasMessageNotContaining("{not-json");
    }

    @Test
    void invariantBreakingJsonMapsToSafeCacheException() {
        assertThatThrownBy(() -> mapper.fromJson(emptyCandidatesJson()))
                .isInstanceOf(CacheException.class)
                .hasMessage("Username recovery workflow state deserialization failed");
        assertThatThrownBy(() -> mapper.fromJson(duplicateSchemeRefsJson()))
                .isInstanceOf(CacheException.class)
                .hasMessage("Username recovery workflow state deserialization failed");
    }

    @Test
    void legacySessionShapedJsonIsRejected() {
        assertThatThrownBy(() -> mapper.fromJson(legacySessionShapedJson()))
                .isInstanceOf(CacheException.class)
                .hasMessageNotContaining("urs_opaque-1")
                .hasMessageNotContaining("csrf_hash_opaque")
                .hasMessageNotContaining("member@example.com");
    }

    @Test
    void safeMessagesDiscloseNoProtectedValue() {
        var jsonWithProtectedValues = mapper.toJson(activeWorkflowState());
        assertThat(jsonWithProtectedValues)
                .contains("user-sch")
                .contains("member@example.com")
                .contains("afs_abc123");

        var corrupted = jsonWithProtectedValues.replace("\"candidates\"", "\"candidates");

        assertThatThrownBy(() -> mapper.fromJson(corrupted))
                .isInstanceOf(CacheException.class)
                .hasMessage("Username recovery workflow state deserialization failed")
                .hasMessageNotContaining("user-sch")
                .hasMessageNotContaining("member@example.com")
                .hasMessageNotContaining("afs_abc123");
    }

    private static String emptyCandidatesJson() {
        return """
                {
                    "authFlowSessionId": "afs_abc123",
                    "status": "ACTIVE",
                    "revision": 0,
                    "destination": "member@example.com",
                    "destinationChannel": "EMAIL",
                    "language": "zh-HK",
                    "candidates": []
                }
                """;
    }

    private static String duplicateSchemeRefsJson() {
        return """
                {
                    "authFlowSessionId": "afs_abc123",
                    "status": "ACTIVE",
                    "revision": 0,
                    "destination": "member@example.com",
                    "destinationChannel": "EMAIL",
                    "language": "zh-HK",
                    "candidates": [
                        {
                            "schemeRef": "sch_opaque-1",
                            "accountEnv": "HK",
                            "trustCode": "trust-1",
                            "registered": true,
                            "userName": "user-sch",
                            "deliveryState": "AVAILABLE",
                            "requestedAt": null,
                            "senderReference": null
                        },
                        {
                            "schemeRef": "sch_opaque-1",
                            "accountEnv": "HK",
                            "trustCode": "trust-2",
                            "registered": true,
                            "userName": "user-sch-2",
                            "deliveryState": "AVAILABLE",
                            "requestedAt": null,
                            "senderReference": null
                        }
                    ]
                }
                """;
    }

    private static String legacySessionShapedJson() {
        return """
                {
                    "sessionId": "urs_opaque-1",
                    "scenario": "USERNAME_RECOVERY",
                    "status": "ACTIVE",
                    "revision": 0,
                    "csrfHash": "csrf_hash_opaque",
                    "destination": "member@example.com",
                    "destinationChannel": "EMAIL",
                    "language": "zh-HK",
                    "createdAt": "2026-08-08T00:00:00Z",
                    "expiresAt": "2026-08-08T00:10:00Z",
                    "candidates": [
                        {
                            "schemeRef": "sch_opaque-1",
                            "accountEnv": "HK",
                            "trustCode": "trust-1",
                            "registered": true,
                            "userName": "user-sch",
                            "deliveryState": "AVAILABLE",
                            "requestedAt": null,
                            "senderReference": null
                        }
                    ]
                }
                """;
    }

    private static UsernameRecoveryWorkflowState activeWorkflowState() {
        var registeredOne = new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef("sch_opaque-1"),
                        "HK", "trust-1", true, "user-sch",
                        UsernameRecoveryDeliveryState.AVAILABLE),
                null, null);
        var registeredTwo = new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef("sch_opaque-2"),
                        "HK", "trust-2", true, "user-sch-2",
                        UsernameRecoveryDeliveryState.AVAILABLE),
                null, null);
        var unregistered = new UsernameRecoverySessionCandidate(
                new UsernameRecoveryCandidate(
                        new UsernameRecoverySchemeRef("sch_opaque-3"),
                        "JP", "trust-3", false, null,
                        UsernameRecoveryDeliveryState.AVAILABLE),
                null, null);
        return new UsernameRecoveryWorkflowState(
                new AuthFlowSessionId("afs_abc123"),
                UsernameRecoveryWorkflowStatus.ACTIVE,
                new UsernameRecoveryWorkflowStateRevision(0),
                new UsernameRecoveryVerifiedDestination("member@example.com",
                        UsernameRecoveryVerifiedDestination.Channel.EMAIL),
                Locale.forLanguageTag("zh-HK"),
                List.of(registeredOne, registeredTwo, unregistered));
    }
}
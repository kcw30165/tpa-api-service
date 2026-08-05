package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import java.time.Instant;
import java.util.Map;

record RedisAuthFlowSessionRecord(
        String authFlowSessionId,
        String scenario,
        String status,
        String csrfTokenHash,
        String activeOtpChallengeId,
        Data data,
        String createdAt,
        String updatedAt,
        String expiresAt,
        String sessionRevision) {

    static RedisAuthFlowSessionRecord from(AuthFlowSession session) {
        return new RedisAuthFlowSessionRecord(
                session.authFlowSessionId().value(),
                session.scenario().name(),
                session.status().name(),
                session.csrfTokenHash(),
                session.activeOtpChallengeId(),
                Data.from(session.data()),
                instantToString(session.createdAt()),
                instantToString(session.updatedAt()),
                instantToString(session.expiresAt()),
                null);
    }

    static RedisAuthFlowSessionRecord from(AuthFlowSession session, SessionRevision revision) {
        RedisAuthFlowSessionRecord legacy = from(session);
        return new RedisAuthFlowSessionRecord(
                legacy.authFlowSessionId(), legacy.scenario(), legacy.status(),
                legacy.csrfTokenHash(), legacy.activeOtpChallengeId(), legacy.data(),
                legacy.createdAt(), legacy.updatedAt(), legacy.expiresAt(), revision.value());
    }

    boolean sessionRevisionPresent() {
        return sessionRevision != null && !sessionRevision.isBlank();
    }

    AuthFlowSession toAuthFlowSession() {
        return new AuthFlowSession(
                new AuthFlowSessionId(authFlowSessionId),
                AuthFlowScenario.valueOf(scenario),
                AuthFlowStatus.valueOf(status),
                csrfTokenHash,
                activeOtpChallengeId,
                data == null ? AuthFlowSessionData.empty() : data.toAuthFlowSessionData(),
                instantFromString(createdAt),
                instantFromString(updatedAt),
                instantFromString(expiresAt));
    }

    record Data(String contactType, String maskedDestination, Map<String, String> attributes) {
        static Data from(AuthFlowSessionData data) {
            if (data == null) {
                return new Data(null, null, Map.of());
            }
            return new Data(data.contactType(), data.maskedDestination(), data.attributes());
        }

        AuthFlowSessionData toAuthFlowSessionData() {
            return new AuthFlowSessionData(contactType, maskedDestination, attributes);
        }
    }

    private static String instantToString(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static Instant instantFromString(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}

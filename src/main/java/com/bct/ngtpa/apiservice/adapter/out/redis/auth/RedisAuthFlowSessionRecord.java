package com.bct.ngtpa.apiservice.adapter.out.redis.auth;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SelectedTrust;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.UsernameCheckState;
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
        String expiresAt) {

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
                instantToString(session.expiresAt()));
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

    record Data(String contactType, String maskedDestination, UsernameCheck usernameCheck, Map<String, String> attributes) {
        static Data from(AuthFlowSessionData data) {
            if (data == null) {
                return new Data(null, null, null, Map.of());
            }
            return new Data(data.contactType(), data.maskedDestination(), UsernameCheck.from(data.usernameCheck()), data.attributes());
        }

        AuthFlowSessionData toAuthFlowSessionData() {
            return new AuthFlowSessionData(contactType, maskedDestination, usernameCheck == null ? null : usernameCheck.toUsernameCheckState(), attributes);
        }
    }

    record UsernameCheck(SelectedTrustRecord selectedTrust, String username, String checkedAt, String expiresAt) {
        static UsernameCheck from(UsernameCheckState state) {
            if (state == null) {
                return null;
            }
            return new UsernameCheck(
                    SelectedTrustRecord.from(state.selectedTrust()),
                    state.username(),
                    instantToString(state.checkedAt()),
                    instantToString(state.expiresAt()));
        }

        UsernameCheckState toUsernameCheckState() {
            return new UsernameCheckState(selectedTrust.toSelectedTrust(), username, instantFromString(checkedAt), instantFromString(expiresAt));
        }
    }

    record SelectedTrustRecord(String trustCode, String accountEnv, String schemeName, String logoKey, Integer displayOrder) {
        static SelectedTrustRecord from(SelectedTrust trust) {
            if (trust == null) {
                return null;
            }
            return new SelectedTrustRecord(trust.trustCode(), trust.accountEnv(), trust.schemeName(), trust.logoKey(), trust.displayOrder());
        }

        SelectedTrust toSelectedTrust() {
            return new SelectedTrust(trustCode, accountEnv, schemeName, logoKey, displayOrder);
        }
    }

    private static String instantToString(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static Instant instantFromString(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}

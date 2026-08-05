package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import java.time.Instant;
import java.util.*;
import reactor.core.publisher.Mono;

public class RegistrationCompletionSessionService {
    private final AuthFlowSessionPort sessions;

    public RegistrationCompletionSessionService(AuthFlowSessionPort sessions) {
        this.sessions = Objects.requireNonNull(sessions);
    }

    public Mono<AuthFlowSession> transition(AuthFlowSession session, AuthFlowStatus status, Instant now,
            boolean clearUsernameCheck) {
        AuthFlowSessionData data = session.data();
        if (clearUsernameCheck) {
            Map<String, String> a = new LinkedHashMap<>(data.attributes());
            a.remove(RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_CIPHERTEXT);
            a.remove(RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_KEY_VERSION);
            a.remove(RegistrationUsernameCheckAttributeNames.USERNAME_CHECK_ALGORITHM);
            data = new AuthFlowSessionData(data.contactType(), data.maskedDestination(), a);
        }
        return new com.bct.ngtpa.apiservice.application.usecase.session.authflow.RevisionAwareAuthFlowSessionWriter(
                sessions).updateExpected(session,
                        new AuthFlowSession(session.authFlowSessionId(), session.scenario(), status,
                                session.csrfTokenHash(),
                                session.activeOtpChallengeId(), data, session.createdAt(), now, session.expiresAt()));
    }
}

package com.bct.ngtpa.apiservice.application.service.auth.usernamerecovery.context;

import static com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContextAttributeNames.*;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.UsernameRecoveryAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryAuthFlowContextProtectorPort;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Framework-free binding between protected Username Recovery state and AuthFlowSessionData. */
public final class UsernameRecoveryAuthFlowContextBinding {
    private final UsernameRecoveryAuthFlowContextProtectorPort protector;
    public UsernameRecoveryAuthFlowContextBinding(UsernameRecoveryAuthFlowContextProtectorPort protector) {
        this.protector = Objects.requireNonNull(protector);
    }
    public AuthFlowSession bind(
            AuthFlowSession session, UsernameRecoveryAuthFlowContext context, Instant updatedAt) {
        UsernameRecoveryAuthFlowEncryptedContext encrypted = protector.protect(
                session.authFlowSessionId(), context);
        Map<String, String> attributes = new LinkedHashMap<>(session.data().attributes());
        attributes.put(CONTEXT_CIPHERTEXT, encrypted.ciphertext());
        attributes.put(CONTEXT_KEY_VERSION, encrypted.keyVersion());
        attributes.put(CONTEXT_ALGORITHM, encrypted.algorithm());
        AuthFlowSessionData data = new AuthFlowSessionData(
                context.verifiedDestination().channel().name(),
                session.data().maskedDestination(), attributes);
        return new AuthFlowSession(session.authFlowSessionId(), session.scenario(), session.status(),
                session.csrfTokenHash(), session.activeOtpChallengeId(), data,
                session.createdAt(), updatedAt, session.expiresAt());
    }
    public UsernameRecoveryAuthFlowContext load(AuthFlowSession session) {
        Map<String, String> a = session.data().attributes();
        String ciphertext = a.get(CONTEXT_CIPHERTEXT);
        String version = a.get(CONTEXT_KEY_VERSION);
        String algorithm = a.get(CONTEXT_ALGORITHM);
        if (blank(ciphertext) || blank(version) || blank(algorithm)) {
            throw new IllegalStateException("Username Recovery protected context is missing");
        }
        return protector.unprotect(session.authFlowSessionId(),
                new UsernameRecoveryAuthFlowEncryptedContext(ciphertext, version, algorithm));
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}

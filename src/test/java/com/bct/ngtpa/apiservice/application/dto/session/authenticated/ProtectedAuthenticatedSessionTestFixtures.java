package com.bct.ngtpa.apiservice.application.dto.session.authenticated;

import java.time.Instant;
import java.util.Map;

public final class ProtectedAuthenticatedSessionTestFixtures {
    private ProtectedAuthenticatedSessionTestFixtures() {
    }

    public static ProtectedAuthenticatedSession envelope(
            String keyVersion,
            String ciphertext,
            String nonce,
            String authenticationTag) {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        return new ProtectedAuthenticatedSession(
                "sid_abcdefghijklmnopqrstuvwx",
                AuthenticatedSessionStatus.ACTIVE,
                new ProtectedAuthenticatedSession.Data(Map.of(
                        ProtectedAuthenticatedSession.CIPHERTEXT, ciphertext,
                        ProtectedAuthenticatedSession.ALGORITHM, "AES/GCM/NoPadding",
                        ProtectedAuthenticatedSession.NONCE, nonce,
                        ProtectedAuthenticatedSession.AUTHENTICATION_TAG, authenticationTag,
                        ProtectedAuthenticatedSession.KEY_VERSION, keyVersion)),
                created,
                created,
                created.plusSeconds(900),
                created.plusSeconds(3600));
    }
}

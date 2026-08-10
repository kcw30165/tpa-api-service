package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TemporaryPortalAccessContextHeaderSessionGuardTest {

    @Test
    void requestHeaderContextCarriesCanonicalAuthenticatedSessionHeader() throws Exception {
        String keys = Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/shared/web/RequestHeaderContextKeys.java"));
        String context = Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/shared/web/RequestHeaderContext.java"));
        String loggingFilter = Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/filter/RequestLoggingWebFilter.java"));

        assertTrue(keys.contains("AUTHENTICATED_SESSION_HEADER"));
        assertTrue(keys.contains("X-Authenticated-Session-Id"));
        assertFalse(keys.contains("public static final String SESSION_ID_HEADER ="));
        assertFalse(keys.contains("\"Session-Id\""));
        assertTrue(context.contains("String sessionId"));
        assertTrue(loggingFilter.contains("RequestHeaderContextKeys.AUTHENTICATED_SESSION_HEADER"));
    }

    @Test
    void portalAccessContextFilterPassesPortalCookieSessionToPort() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/filter/PortalAccessContextWebFilter.java"))
                .replaceAll("\\s+", "");
        org.junit.jupiter.api.Assertions.assertTrue(source.contains("sessionCookies.cookieName()"));
        org.junit.jupiter.api.Assertions.assertTrue(
                source.contains("resolvePortalAccessContext(accountRef,sessionId)"));
        org.junit.jupiter.api.Assertions.assertFalse(source.contains("AUTHENTICATED_SESSION_HEADER"));
        org.junit.jupiter.api.Assertions.assertFalse(source.contains("X-Authenticated-Session-Id"));
    }

    @Test
    void temporaryAdapterIsRemoved() throws Exception {
        java.nio.file.Path p = Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/security/TemporaryPortalAccessContextAdapter.java");
        assertFalse(Files.exists(p), "TemporaryPortalAccessContextAdapter must be removed in Stage 003");
    }
}

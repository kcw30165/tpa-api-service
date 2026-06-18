package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TemporaryPortalAccessContextHeaderSessionGuardTest {

    @Test
    void requestHeaderContextCarriesTemporarySessionIdHeader() throws Exception {
        String keys = Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/shared/web/RequestHeaderContextKeys.java"));
        String context = Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/shared/web/RequestHeaderContext.java"));
        String loggingFilter = Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/filter/RequestLoggingWebFilter.java"));

        assertTrue(keys.contains("SESSION_ID_HEADER"));
        assertTrue(keys.contains("sid_xxx"));
        assertTrue(context.contains("String sessionId"));
        assertTrue(loggingFilter.contains("RequestHeaderContextKeys.SESSION_ID_HEADER"));
    }

    @Test
    void portalAccessContextFilterPassesHeaderSessionIdToPort() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/filter/PortalAccessContextWebFilter.java"));

        assertTrue(source.contains("resolveSessionId(exchange)"));
        assertTrue(source.contains("resolvePortalAccessContext(accountRef, resolveSessionId(exchange))"));
    }

    @Test
    void temporaryAdapterDoesNotUseConfiguredDefaultSessionIdForSessionSelection() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/security/TemporaryPortalAccessContextAdapter.java"));

        assertTrue(source.contains("resolvePortalAccessContext(String accountRef, String sessionId)"));
        assertTrue(source.contains("resolveFromSessionShape(normalizedAccountRef, sessionId)"));
        assertTrue(source.contains("resolveHeaderSessionId"));
        assertFalse(source.contains("getDefaultSessionId()"));
        assertFalse(source.contains("resolveDefaultSessionId"));
    }
}

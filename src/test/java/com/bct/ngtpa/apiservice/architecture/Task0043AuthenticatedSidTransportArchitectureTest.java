package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043AuthenticatedSidTransportArchitectureTest {
    @Test
    void canonicalAuthenticatedTransportIsSidXxx() throws Exception {
        String keys = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/shared/web/RequestHeaderContextKeys.java"));
        assertThat(keys).contains("AUTHENTICATED_SESSION_HEADER = \"X-Authenticated-Session-Id\"");
    }

    @Test
    void verifyControllerPublishesAuthenticatedCookiesOnlyFromSuccessfulInternalResult() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLoginOtpVerifyController.java"));
        org.assertj.core.api.Assertions.assertThat(source)
                .contains("VerifyLoginOtpStatus.ACCOUNTS_READY",
                        "result.authenticatedSessionId().value()",
                        "result.authenticatedCsrfToken()",
                        "authenticatedSessionCookies.write",
                        "authenticatedCsrfCookies.write",
                        "clearAuthFlowCookie",
                        "clearXsrfCookie")
                .doesNotContain("AUTHENTICATED_SESSION_HEADER",
                        "X-Authenticated-Session-Id");
    }
}

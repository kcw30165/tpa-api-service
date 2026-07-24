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
        assertThat(keys).contains("AUTHENTICATED_SESSION_HEADER = \"sid_xxx\"");
    }

    @Test
    void verifyControllerPublishesSidOnlyFromSuccessfulInternalResult() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/AuthLoginOtpVerifyController.java"));
        assertThat(source).contains(
                "VerifyLoginOtpStatus.ACCOUNTS_READY",
                "result.authenticatedSessionId().value()",
                "RequestHeaderContextKeys.AUTHENTICATED_SESSION_HEADER",
                "clearAuthFlowCookie",
                "clearXsrfCookie");
        assertThat(source).doesNotContain("Session-Id");
    }
}

package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Task0043LoginOtpSendIdempotencyArchitectureTest {
    @Test
    void safeReplayContainsOnlyPublicResponseMetadata() throws Exception {
        String s = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/idempotency/LoginOtpSendReplaySnapshot.java"));
        assertThat(s).contains("maskedDestination", "expiresInSeconds", "resendCooldownSeconds");
        assertThat(s).doesNotContain("sessionId", "csrf", "destinationId", "rawCode", "provider");
    }

    @Test
    void decoratorReservesBeforeDelegateAndHasNoInfrastructureDependency() throws Exception {
        String s = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/IdempotentLoginOtpSendService.java"));
        assertThat(s).contains("idempotency.reserve", "delegate.send", "COMPLETED_REPLAY");
        assertThat(s).doesNotContain("Redis", "WebClient", "org.springframework");
    }

    @Test
    void hashDoesNotIncludeCsrfOrRawDestination() throws Exception {
        String s = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/LoginOtpSendRequestHashGenerator.java"));
        assertThat(s).contains("authFlowSessionId()", "destinationId()");
        assertThat(s).doesNotContain("csrfToken()", "origin()", "referer()", "acceptLanguage()");
    }
}

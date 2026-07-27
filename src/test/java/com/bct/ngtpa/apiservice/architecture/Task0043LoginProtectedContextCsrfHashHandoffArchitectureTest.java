package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Task0043LoginProtectedContextCsrfHashHandoffArchitectureTest {
    @Test
    void loaderReturnsPersistedHashButNeverRawSubmittedToken() throws Exception {
        String l = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/service/auth/login/context/DefaultLoginProtectedAuthFlowContextLoader.java"));
        String r = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/LoginProtectedAuthFlowContextResult.java"));
        assertThat(l.replaceAll("\\s+", "")).contains(
                "LoginProtectedAuthFlowContextResult.valid(context,s.activeOtpChallengeId(),selected,s.csrfTokenHash())");
        assertThat(r).contains("String csrfTokenHash").doesNotContain("String csrfToken,");
    }

    @Test
    void otpTransitionUsesPersistedCsrfHash() throws Exception {
        String s = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/verify/LoginOtpVerifyService.java"));
        assertThat(s).contains("EstablishAuthenticatedLoginSessionUseCase", "x.csrfTokenHash()")
                .doesNotContain("c.csrfToken(),c.correlationId())");
    }
}

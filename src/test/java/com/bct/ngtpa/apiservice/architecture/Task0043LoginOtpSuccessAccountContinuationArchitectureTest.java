package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Task0043LoginOtpSuccessAccountContinuationArchitectureTest {
        @Test
        void verifiedContinuationReusesExistingAccountCapabilities() throws Exception {
                String s = Files.readString(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/verify/LoginOtpVerifyService.java"));
                assertThat(s)
                                .contains("GetLoginAccountsUseCase", "LoginAccountRecordValidationService",
                                                "LoginAccountReferenceAssignmentService", "normalizedUsername()")
                                .doesNotContain("WebClient", "Redis", "AuthenticatedSessionPort");
        }

        @Test
        void publicAccountProjectionContainsOnlyAccountRef() throws Exception {
                String s = Files.readString(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/dto/LoginOtpAccountResponse.java"));
                assertThat(s).contains("String accountRef", "policyNo", "certNo", "accountEnv", "trustCode",
                                "schemeType");
        }

        @Test
        void authenticatedSessionTransitionIsDelivered() throws Exception {
                assertThat(Files.exists(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/application/dto/session/authenticated/AuthenticatedSession.java")))
                                .isTrue();
                assertThat(Files.readString(Path.of(
                                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/verify/LoginOtpVerifyService.java")))
                                .contains("EstablishAuthenticatedLoginSessionUseCase", "x.csrfTokenHash()",
                                                "authenticatedSessionId()");
        }
}

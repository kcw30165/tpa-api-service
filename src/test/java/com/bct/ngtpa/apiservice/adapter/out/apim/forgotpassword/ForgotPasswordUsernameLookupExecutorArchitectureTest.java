package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForgotPasswordUsernameLookupExecutorArchitectureTest {
    @Test
    void pilotUsesExecutorAndExplicitRoutingWithoutManualCrypto() throws Exception {
        String s = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/forgotpassword/ApimForgotPasswordUsernameLookupAdapter.java"));
        assertThat(s).contains("ApimExchangeExecutor", "ApimDatabaseScope.NON_ACCOUNT",
                "ApimRetryEligibility.SAFE_BUSINESS_CALL",
                "new ApimCredentialResolutionContext(command.actorUserRole(), null)");
        assertThat(s).doesNotContain("ApimWebClientFacade", "ApimCertificateService", "ApimPayloadCryptoService",
                "getEncryption()", "ROLE_USER_MBR");
    }

    @Test
    void trustedMapperOwnsRoleAndFeDtoDoesNot() throws Exception {
        String m = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/mapper/ForgotPasswordStartWebMapper.java"));
        String d = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/dto/ForgotPasswordStartRequest.java"));
        assertThat(m).contains("ActorUserRole.ROLE_USER_MBR");
        assertThat(d).doesNotContain("ActorUserRole", "actorRole", "scope", "accountEnv");
    }

    @Test
    void registrationAndSharedTransportRemainUnchangedInStage05() throws Exception {
        assertThat(Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/registration/ApimRegistrationUsernameAvailabilityAdapter.java")))
                .contains("SharedApimTrpChkCustIdTransport", "postWithAccountEnv");
    }
}

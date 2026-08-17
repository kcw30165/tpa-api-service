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
    void stage17RegistrationUsesTheSharedProductionPortWithoutAccountEnvRouting() throws Exception {
        String adapter = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/registration/ApimRegistrationUsernameAvailabilityAdapter.java"));
        String transport = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/registration/SharedApimTrpChkCustIdTransport.java"));

        assertThat(adapter)
                .contains("TrpChkCustIdTransport transport", "command.actorUserRole()")
                .doesNotContain("postWithAccountEnv", "selectedTrust().accountEnv()");
        assertThat(transport)
                .contains("ApimExchangeExecutor", "ApimDatabaseScope.NON_ACCOUNT",
                        "new ApimCredentialResolutionContext(command.actorUserRole(), null)",
                        "ApimRetryEligibility.SAFE_BUSINESS_CALL")
                .doesNotContain("postWithAccountEnv", "ApimDatabaseScope.ACCOUNT_ENV", "ActorUserRole.ROLE_USER_MBR");
    }
}

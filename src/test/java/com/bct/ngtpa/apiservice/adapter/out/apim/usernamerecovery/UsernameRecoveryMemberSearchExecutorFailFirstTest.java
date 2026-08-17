package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UsernameRecoveryMemberSearchExecutorFailFirstTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/", relative));
    }
    private static String compact(String value) { return value.replaceAll("\\s+", ""); }

    @Test
    void lookupCommandCarriesExplicitPreAuthenticationRole() throws Exception {
        String command = compact(source("application/dto/auth/usernamerecovery/UsernameRecoveryLookupCommand.java"));
        assertThat(command)
                .contains("ActorUserRoleactorUserRole")
                .contains("Objects.requireNonNull(actorUserRole")
                .doesNotContain("PortalAccessContext")
                .doesNotContain("accountRef");
    }

    @Test
    void startServiceSuppliesCompatibilityRoleOnlyAfterCaptchaGate() throws Exception {
        String service = compact(source("application/usecase/auth/usernamerecovery/StartUsernameRecoveryService.java"));
        assertThat(service)
                .contains("ActorUserRole.ROLE_USER_MBR")
                .contains("newUsernameRecoveryLookupCommand(")
                .contains("captchaGateService.consume(")
                .contains("lookupPort.search(");
        assertThat(service.indexOf("captchaGateService.consume("))
                .isLessThan(service.indexOf("lookupPort.search("));
    }

    @Test
    void adapterUsesTypedNonAccountSafeBusinessExecutorRequest() throws Exception {
        String adapter = compact(source("adapter/out/apim/usernamerecovery/TrpSearchMemberAdapter.java"));
        assertThat(adapter)
                .contains("ApimExchangeExecutor")
                .contains("ApimExchangeRequest")
                .contains("postEnvelope(")
                .contains("/ws/NGTPA/v1/TRPSearchMember")
                .contains("TrpSearchMemberApimRequest.class")
                .contains("TrpSearchMemberApimDataItem.class")
                .contains("ApimDatabaseScope.NON_ACCOUNT")
                .contains("newApimCredentialResolutionContext(command.actorUserRole(),null)")
                .contains("ApimRetryEligibility.SAFE_BUSINESS_CALL")
                .doesNotContain("ActorUserRole.ROLE_USER_MBR")
                .doesNotContain("TrpSearchMemberTransport");
    }

    @Test
    void migratedProductionPathOwnsNoManualCryptoOrProfileBranch() throws Exception {
        String adapter = source("adapter/out/apim/usernamerecovery/TrpSearchMemberAdapter.java");
        Path legacyTransport = Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/usernamerecovery/SharedApimTrpSearchMemberTransport.java");
        assertThat(Files.exists(legacyTransport)).isFalse();
        assertThat(adapter)
                .doesNotContain("ApimWebClientFacade")
                .doesNotContain("ApimCertificateService")
                .doesNotContain("ApimPayloadCryptoService")
                .doesNotContain("ApimProperties")
                .doesNotContain("resolveProfile(")
                .doesNotContain("encryptRequest(")
                .doesNotContain("decryptResponseEnvelope(");
    }

    @Test
    void requestBodyDoesNotGainRoutingCaptchaOtpOrPublicResponseFields() throws Exception {
        String request = source("adapter/out/apim/usernamerecovery/dto/TrpSearchMemberApimRequest.java");
        assertThat(request)
                .contains("search-by", "search-value", "dob")
                .doesNotContain("actorUserRole")
                .doesNotContain("accountEnv")
                .doesNotContain("captcha")
                .doesNotContain("otp")
                .doesNotContain("userName");
    }
}

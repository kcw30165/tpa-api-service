package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationUsernameAvailabilityExecutorFailFirstTest {
    private static String read(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/", relative));
    }

    private static String compact(String source) {
        return source.replaceAll("\\s+", "");
    }

    @Test
    void registrationOutboundCommandCarriesExplicitCredentialRoutingRole() throws Exception {
        String source = compact(read("application/usecase/auth/registration/username/RegistrationUsernameAvailabilityCommand.java"));
        assertThat(source)
                .contains("ActorUserRoleactorUserRole")
                .contains("Objects.requireNonNull(actorUserRole");
    }

    @Test
    void registrationServiceIntroducesApprovedPreAuthenticationCompatibilityRole() throws Exception {
        String source = compact(read("application/usecase/auth/registration/username/CheckRegistrationUsernameService.java"));
        assertThat(source)
                .contains("ActorUserRole.ROLE_USER_MBR")
                .contains("newRegistrationUsernameAvailabilityCommand(");
    }

    @Test
    void sharedTransportUsesTypedNonAccountExchangeWithoutRoleSubstitution() throws Exception {
        String source = compact(read("adapter/out/apim/registration/SharedApimTrpChkCustIdTransport.java"));
        assertThat(source)
                .contains("ApimExchangeExecutor")
                .contains("ApimExchangeRequest")
                .contains("postEnvelope(")
                .contains("ApimDatabaseScope.NON_ACCOUNT")
                .contains("command.actorUserRole()")
                .contains("ApimRetryEligibility.SAFE_BUSINESS_CALL")
                .contains("TrpChkCustIdApimRequest.class")
                .contains("TrpChkCustIdApimDataItem.class")
                .doesNotContain("ActorUserRole.ROLE_USER_MBR")
                .doesNotContain("ApimWebClientFacade")
                .doesNotContain("ApimCertificateService")
                .doesNotContain("ApimPayloadCryptoService")
                .doesNotContain("ApimProperties");
    }

    @Test
    void registrationAdapterDoesNotRouteFromSelectedTrustAccountEnvironment() throws Exception {
        String source = compact(read("adapter/out/apim/registration/ApimRegistrationUsernameAvailabilityAdapter.java"));
        assertThat(source)
                .doesNotContain("postWithAccountEnv")
                .doesNotContain("selectedTrust().accountEnv()")
                .contains("command.actorUserRole()");
    }

    @Test
    void forgotPasswordKeepsExplicitIndependentRoleAndExecutorContract() throws Exception {
        String source = compact(read("adapter/out/apim/forgotpassword/ApimForgotPasswordUsernameLookupAdapter.java"));
        assertThat(source)
                .contains("command.actorUserRole()")
                .contains("ApimDatabaseScope.NON_ACCOUNT")
                .contains("ApimRetryEligibility.SAFE_BUSINESS_CALL")
                .contains("postEnvelope(");
    }
}

package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043TrpGetAccountListAdapterArchitectureTest {
    @Test
    void transportReusesSharedApimFacadeCryptoCertificateAndRetry() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/account/SharedApimTrpGetAccountListTransport.java"));
        assertThat(source).contains(
                "ApimWebClientFacade", "ApimCertificateService", "ApimPayloadCryptoService",
                "ApimRetryEligibility.SAFE_BUSINESS_CALL", "ApimCredentialResolutionContext");
        assertThat(source).doesNotContain(
                "WebClient.builder", "OAuth", "new Cipher", "TRPMemberLogin");
    }

    @Test
    void adapterImplementsApplicationPortAndDoesNotLeakRawErrors() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/account/TrpGetAccountListAdapter.java"));
        assertThat(source).contains(
                "implements LoginAccountAuthorizationPort", "errorMappings");
        assertThat(source).doesNotContain(
                "err.tpa.no.active.account", "err.tpa.no.valid.login.ac",
                "AccountRef", "portalSession", "HttpStatus");
    }

    @Test
    void exactDownstreamMessagesAreConfigurationOwned() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yaml).contains(
                "get-account-list:",
                "err.tpa.no.active.account: UNAVAILABLE",
                "err.tpa.no.valid.login.ac: UNAVAILABLE");
    }
}

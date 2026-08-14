package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TrpSearchMemberAdapterArchitectureTest {

    @Test
    void adapterReusesSharedApimTransportInfrastructureAndSafeRetry() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/usernamerecovery/TrpSearchMemberAdapter.java"));

        assertThat(source).contains(
                "implements UsernameRecoveryLookupPort",
                "TrpSearchMemberTransport",
                "TrpSearchMemberApimMapper",
                "TrpSearchMemberApimResultMapper",
                "RequestCorrelation.REQUEST_ID_CONTEXT_KEY");
        assertThat(source).doesNotContain(
                "ApimWebClientFacade",
                "ApimCertificateService",
                "ApimPayloadCryptoService",
                "ApimRetryEligibility.SAFE_BUSINESS_CALL",
                "ApimCredentialResolutionContext",
                "WebClient.builder",
                "WebClient.create",
                "new Cipher",
                "OAuth",
                "TrpSearchMemberApimResponseBody",
                "DOWNSTREAM_REJECTED",
                "DOWNSTREAM_ERROR");
    }

    @Test
    void portExposesOnlyApplicationContracts() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/out/UsernameRecoveryLookupPort.java"));

        assertThat(source).contains(
                "Mono<UsernameRecoveryLookupResult> search(UsernameRecoveryLookupCommand command)");
        assertThat(source).doesNotContain(
                "ApimResponseEnvelope", "TrpSearchMemberApimDataItem", "WebClient", "HttpStatus");
    }
    @Test
    void transportImplementationIsRegisteredAsSpringBean() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/usernamerecovery/SharedApimTrpSearchMemberTransport.java"));
        assertThat(source).contains(
                "@Component",
                "implements TrpSearchMemberTransport");
    }

}

package com.bct.ngtpa.apiservice.adapter.out.apim.login;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SharedApimTrpMemberLoginTransportArchitectureTest {

    @Test
    void transportReusesSharedEnvelopeCryptoCertificateFacadeAndSafeRetry() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/SharedApimTrpMemberLoginTransport.java"));

        assertThat(source).contains(
                "ApimResponseEnvelope",
                "ApimPayloadCryptoService",
                "ApimCertificateService",
                "ApimWebClientFacade",
                "ApimRetryEligibility.SAFE_BUSINESS_CALL");
        assertThat(source).doesNotContain(
                "WebClient.builder",
                "retryWhen(",
                "OAuth",
                "new ApimResponseEnvelope");
    }

    @Test
    void adapterPrivateDtoMatchesConfirmedProgressFieldNames() throws Exception {
        String request = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/dto/TrpMemberLoginApimRequest.java"));
        String item = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/dto/TrpMemberLoginApimDataItem.java"));

        assertThat(request).contains("@JsonProperty(\"cust-id\")", "@JsonProperty(\"password\")");
        assertThat(request).doesNotContain("language", "requestId", "actorReference");
        assertThat(item).contains(
                "@JsonProperty(\"user-role\")",
                "@JsonProperty(\"cust-type\")",
                "@JsonProperty(\"fail-count\")",
                "@JsonProperty(\"otp\")");
    }
}

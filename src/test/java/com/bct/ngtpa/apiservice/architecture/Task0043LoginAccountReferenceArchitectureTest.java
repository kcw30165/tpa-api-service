package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginAccountReferenceArchitectureTest {
    @Test
    void accountRefGenerationIsAnOutboundSecurityCapability() throws Exception {
        String port = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/out/LoginAccountRefGeneratorPort.java"));
        String generator = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/security/login/account/SecureRandomLoginAccountRefGenerator.java"));
        assertThat(port).doesNotContain(
                "SecureRandom", "Base64", "org.springframework", "adapter.out");
        assertThat(generator).contains("SecureRandom", "Base64.getUrlEncoder().withoutPadding()");
        assertThat(generator).doesNotContain("policyNo", "certNo", "accountEnv", "trustCode");
    }

    @Test
    void assignmentDoesNotPersistSessionsOrCallApim() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/account/LoginAccountReferenceAssignmentService.java"));
        assertThat(source).doesNotContain(
                "Redis", "WebClient", "Apim", "AuthenticatedSessionPort",
                "AuthFlowSession", "org.springframework");
    }

    @Test
    void accountRefsAreNotAddedToGetAccountListTransportContracts() throws Exception {
        String raw = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/account/LoginAccountRecord.java"));
        String request = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/account/dto/TrpGetAccountListApimRequest.java"));
        assertThat(raw + request).doesNotContain("accountRef", "AccountRef");
    }

    @Test
    void entropyAndPrefixAreConfigurationOwned() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yaml).contains(
                "account-ref:", "prefix: acc", "entropy-bytes: 24", "max-generation-attempts: 8");
    }
}

package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginAccountRecordValidationArchitectureTest {
    @Test
    void rawApimRecordRemainsPermissiveAndFormattingLivesInApplicationService() throws Exception {
        String raw = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/account/LoginAccountRecord.java"));
        String mapper = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/account/TrpGetAccountListContractMapper.java"));
        String service = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/account/LoginAccountRecordValidationService.java"));
        assertThat(raw).doesNotContain("must be positive", "must not be blank");
        assertThat(mapper).doesNotContain("required(", "IllegalStateException");
        assertThat(service).contains("DateTimeFormatter", "ResolverStyle.STRICT", "leftPad");
        assertThat(service).doesNotContain("org.springframework", "adapter.out", "Redis", "WebClient");
    }

    @Test
    void validatorDoesNotGenerateAccountRefsOrCreateSessions() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/account/LoginAccountRecordValidationService.java"));
        assertThat(source).doesNotContain(
                "accountRef", "AccountRef", "portalSession", "AuthenticatedSession",
                "AuthFlowSession", "csrfToken");
    }

    @Test
    void identifierFormattingIsConfigurationOwned() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertThat(yaml).contains(
                "account-identifiers:",
                "policy-no:",
                "width: 11",
                "cert-no:",
                "width: 4",
                "pad-character: \"0\"");
    }
}

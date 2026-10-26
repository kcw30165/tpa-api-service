package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginAccountRecordValidationArchitectureTest {
  @Test
  void rawApimRecordRemainsPermissiveAndFormattingLivesInApplicationService() throws Exception {
    String raw =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/account/LoginAccountRecord.java"));
    String mapper =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/account/TrpGetAccountListContractMapper.java"));
    String service =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/account/LoginAccountRecordValidationService.java"));
    assertThat(raw).doesNotContain("must be positive", "must not be blank");
    assertThat(mapper).doesNotContain("required(", "IllegalStateException");
    String formatter =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/service/account/"
                    + "AccountIdentifierFormatter.java"));
    assertThat(service)
        .contains(
            "DateTimeFormatter",
            "ResolverStyle.STRICT",
            "accountIdentifierFormatter",
            "formatPolicyNo",
            "formatCertificateNo");
    assertThat(service).doesNotContain("leftPad");
    assertThat(formatter).contains("leftPad");
    assertThat(formatter).doesNotContain("org.springframework", "adapter.out", "Redis", "WebClient");
    assertThat(service).doesNotContain("org.springframework", "adapter.out", "Redis", "WebClient");
  }

  @Test
  void validatorDoesNotGenerateAccountRefsOrCreateSessions() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/account/LoginAccountRecordValidationService.java"));
    assertThat(source)
        .doesNotContain(
            "accountRef",
            "AccountRef",
            "portalSession",
            "AuthenticatedSession",
            "AuthFlowSession",
            "csrfToken");
  }

  @Test
  void identifierFormattingIsConfigurationOwned() throws Exception {
    String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
    assertThat(yaml)
        .contains(
            "account-identifiers:",
            "policy-no:",
            "width: 11",
            "cert-no:",
            "width: 4",
            "pad-character: \"0\"");
  }

  @Test
  void identifierFormattingIsGlobalRatherThanLoginScoped() throws Exception {
    String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
    String config =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/config/account/AccountIdentifierConfig.java"));

    // Declared at column zero, so it is a root key rather than nested under
    // auth-workflows.login. Padding applies wherever identifiers are rendered.
    assertThat(yaml).contains("\naccount-identifiers:\n");
    assertThat(config).contains("@ConfigurationProperties(\"account-identifiers\")");
    assertThat(config).doesNotContain("auth-workflows.login.account-identifiers");
  }

  @Test
  void unusedLoginAccountIdentifierWidthIsRemoved() throws Exception {
    String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
    String properties =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/config/"
                    + "AuthWorkflowProperties.java"));

    // auth-workflows.login.account was validated at startup but read by no
    // business code; the live widths live in the global block above.
    assertThat(yaml).doesNotContain("identifier-width", "identifier-pad-character");
    assertThat(properties).doesNotContain("LoginAccount", "identifierWidth");
  }
}

package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginPreAuthSecurityArchitectureTest {

  @Test
  void contextContainsApprovedFieldsAndNoPasswordOrRawOtp() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/auth/login/preauth/LoginPreAuthContext.java"));
    assertThat(source)
        .contains(
            "normalizedUsername",
            "userRole",
            "destinationsById",
            "credentialVerifiedAt",
            "workflowVersion");
    assertThat(source).doesNotContain("password", "rawOtp", "accountRef");
  }

  @Test
  void protectionUsesSharedGenericBoundaryAndDedicatedLoginSecret() throws Exception {
    String port =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/out/LoginPreAuthContextProtectorPort.java"));
    String properties =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/security/login/config/LoginPreAuthContextProtectionProperties.java"));
    assertThat(port).contains("AuthFlowProtectedContextPort");
    assertThat(properties).contains("auth-workflows.login.context-protection");
    assertThat(properties).doesNotContain("registration");
  }
}

package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043AuthenticatedSidTransportArchitectureTest {

  @Test
  void canonicalAuthenticatedTransportIsPortalSidCookieAndBodyCsrf() throws Exception {
    String controller =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/LoginController.java"));
    String sessionCookieWriter =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/session/"
                    + "AuthenticatedSessionCookieWriter.java"));
    String responseData =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/dto/"
                    + "LoginOtpVerifyDataResponse.java"));

    assertThat(controller)
        .contains("authenticatedSessionCookieWriter.write", "r.authenticatedCsrfToken()")
        .doesNotContain(
            "AUTHENTICATED_SESSION_HEADER",
            "X-Authenticated-Session-Id",
            "AuthenticatedCsrfCookieWriter",
            "authenticatedCsrfCookies");
    String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

    assertThat(sessionCookieWriter)
        .contains(
            "AuthWorkflowProperties.AuthenticatedCookie",
            "properties.getName()",
            "cookieName()",
            ".httpOnly(true)");
    assertThat(applicationYaml).contains("AUTH_WORKFLOWS_LOGIN_SESSION_COOKIE_NAME:portal_sid");
    assertThat(responseData).contains("String csrfToken");
  }

  @Test
  void verifyControllerPublishesPortalCookieOnlyFromSuccessfulInternalResult() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/LoginController.java"));

    assertThat(source)
        .contains(
            "VerifyLoginOtpStatus.ACCOUNTS_READY",
            "r.authenticatedSessionId().value()",
            "r.authenticatedCsrfToken()",
            "authenticatedSessionCookieWriter.write",
            "clearAuthFlowCookie",
            "clearXsrfCookie")
        .doesNotContain(
            "AUTHENTICATED_SESSION_HEADER",
            "X-Authenticated-Session-Id",
            "AuthenticatedCsrfCookieWriter",
            "authenticatedCsrfCookies",
            "authenticatedCsrfCookies.write");
  }
}

package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043TrpMemberLoginBusinessErrorMappingTest {
  @Test
  void businessErrorsPrecedeSuccessValidation() throws Exception {
    String s =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/TrpMemberLoginAdapter.java"));
    String c = s.replaceAll("\\s+", "");
    assertThat(s)
        .contains(
            "err.tpa.wrong.password", "err.tpa.cust.id.not.registered", "err.tpa.account.locked");
    int classify = c.indexOf("CredentialAuthenticationStatusbusinessStatus=statusFor(error)");
    int validate = c.indexOf("data==null||data.size()!=1||data.get(0)==null");
    assertThat(classify).isGreaterThanOrEqualTo(0);
    assertThat(validate).isGreaterThan(classify);
    assertThat(c)
        .contains(
            "mapper.credentialFailure(businessStatus,data.get(0).failCount())", "item.failCount()");
  }

  @Test
  void accountLockIs423AndCredentialRejectionIs401() throws Exception {
    String c =
        Files.readString(
                Path.of(
                    "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/LoginHttpResponses.java"))
            .replaceAll("\\s+", "");
    assertThat(c)
        .contains(
            "LoginErrorKey.ACCOUNT_LOCKED.value()))returnHttpStatus.LOCKED;",
            "LoginErrorKey.INVALID_ACCOUNT.value()",
            "LoginErrorKey.CREDENTIALS_INVALID.value()",
            "returnHttpStatus.UNAUTHORIZED;");
  }

  @Test
  void publicMapperContainsNoRawApimCodes() throws Exception {
    String s =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/mapper/LoginStartWebMapper.java"));
    assertThat(s).doesNotContain("err.tpa.");
  }
}

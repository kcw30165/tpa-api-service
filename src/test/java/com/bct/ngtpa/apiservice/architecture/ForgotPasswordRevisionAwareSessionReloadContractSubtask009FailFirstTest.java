package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ForgotPasswordRevisionAwareSessionReloadContractSubtask009FailFirstTest {

  @Test
  void forgotPasswordIdentityAndDetailsSlicesUseRevisionAwareReloads() throws Exception {
    assertRevisionAware(
        "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/identity/VerifyForgotPasswordIdentityService.java");
    assertRevisionAware(
        "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/detailsotp/ForgotPasswordDetailsOtpProtectedOperation.java");
    assertRevisionAware(
        "src/main/java/com/bct/ngtpa/apiservice/adapter/out/session/forgotpassword/ForgotPasswordOtpSessionBindingAdapter.java");
  }

  private static void assertRevisionAware(String relativePath) throws Exception {
    String text = Files.readString(Path.of(relativePath));
    assertThat(text).contains("loadSnapshot(");
    assertThat(text).doesNotContain("sessions.load(");
  }
}

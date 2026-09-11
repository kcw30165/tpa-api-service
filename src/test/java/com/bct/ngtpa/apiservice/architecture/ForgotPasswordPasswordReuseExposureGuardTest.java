package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guards that the Forgot Password password-reuse rejection never exposes downstream vocabulary to
 * the frontend. The raw {@code err.tpa.*} downstream message must stay internal; the public error
 * must be a BFF-owned business code with a non-downstream error type.
 */
class ForgotPasswordPasswordReuseExposureGuardTest {
  private static final Path OPERATION =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/complete/ForgotPasswordCompleteProtectedOperation.java");
  private static final Path KEYS =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/complete/ForgotPasswordCompleteErrorKeys.java");

  @Test
  void passwordReuseEmitsBffOwnedBusinessCodeNotDownstreamVocabulary() throws Exception {
    String keys = Files.readString(KEYS);
    assertThat(keys)
        .contains("PASSWORD_REUSED = \"err.forgot-password.password.reused\"")
        .contains("DOWNSTREAM_PASSWORD_REUSED = \"err.tpa.password.used.in.last.5.changes.\"");

    String op = Files.readString(OPERATION);
    assertThat(op)
        .contains(
            "ForgotPasswordCompleteErrorKeys.DOWNSTREAM_PASSWORD_REUSED.equals(");

    int reuseIdx = op.indexOf("private CompleteForgotPasswordResult passwordReused()");
    assertThat(reuseIdx).isGreaterThan(0);
    int memberIdx = op.indexOf("private CompleteForgotPasswordResult memberNotFound()", reuseIdx);
    assertThat(memberIdx).isGreaterThan(reuseIdx);
    String reuseBlock = op.substring(reuseIdx, memberIdx);
    assertThat(reuseBlock)
        .contains("WorkflowErrorType.BUSINESS")
        .contains("ForgotPasswordCompleteErrorKeys.PASSWORD_REUSED")
        .contains("\"password\"")
        .doesNotContain("DOWNSTREAM_BUSINESS");
  }
}

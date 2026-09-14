package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guards that Forgot Password COMPLETE never exposes downstream vocabulary to the frontend. Raw
 * {@code err.tpa.*} downstream messages stay internal (matching only); the public error codes are
 * BFF-owned with a non-downstream {@code BUSINESS} error type.
 */
class ForgotPasswordPasswordReuseExposureGuardTest {
  private static final Path OPERATION =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/complete/ForgotPasswordCompleteProtectedOperation.java");
  private static final Path KEYS =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/complete/ForgotPasswordCompleteErrorKeys.java");

  @Test
  void feKeyClassHoldsOnlyBffOwnedCodes() throws Exception {
    String keys = Files.readString(KEYS);
    assertThat(keys)
        .contains("PASSWORD_INVALID = \"err.forgot-password.password.invalid\"")
        .contains("PASSWORD_REUSED = \"err.forgot-password.password.reused\"")
        .contains("MEMBER_NOT_FOUND = \"err.forgot-password.member.notFound\"");
    assertThat(keys).doesNotContain("err.tpa.");
  }

  @Test
  void operationMatchesRawDownstreamCodesInternallyOnly() throws Exception {
    String op = Files.readString(OPERATION);
    assertThat(op)
        .contains("DOWNSTREAM_PASSWORD_INVALID = \"err.tpa.password.invalid\"")
        .contains("\"err.tpa.password.used.in.last.5.changes.\"")
        .contains("DOWNSTREAM_MEMBER_NOT_FOUND = \"err.tpa.member.not.found\"")
        .contains("DOWNSTREAM_PASSWORD_INVALID.equals(result.downstreamErrorCode())")
        .contains("DOWNSTREAM_PASSWORD_REUSED.equals(result.downstreamErrorCode())")
        .contains("DOWNSTREAM_MEMBER_NOT_FOUND.equals(result.downstreamErrorCode())");
  }

  @Test
  void publicEmittersUseBusinessTypeNotDownstreamBusiness() throws Exception {
    String op = Files.readString(OPERATION);
    for (String emitter :
        new String[] {"passwordInvalid()", "passwordReused()", "memberNotFound()"}) {
      int start = op.indexOf("private CompleteForgotPasswordResult " + emitter);
      assertThat(start).as(emitter + " must exist").isGreaterThan(0);
      int end = op.indexOf("  private CompleteForgotPasswordResult ", start + 1);
      if (end < 0) {
        end = op.length();
      }
      String block = op.substring(start, end);
      assertThat(block)
          .as(emitter + " must not emit downstream type")
          .doesNotContain("DOWNSTREAM_BUSINESS");
      assertThat(block)
          .as(emitter + " must emit BUSINESS type")
          .contains("WorkflowErrorType.BUSINESS");
    }
  }
}

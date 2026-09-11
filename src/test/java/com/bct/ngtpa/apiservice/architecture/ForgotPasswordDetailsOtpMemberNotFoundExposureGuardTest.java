package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guards that the Forgot Password DETAILS-OTP web mapper never places downstream vocabulary
 * ({@code err.tpa.*}) on the wire. The business-rejected (member-not-found) response must use a
 * BFF-owned code.
 */
class ForgotPasswordDetailsOtpMemberNotFoundExposureGuardTest {
  private static final Path MAPPER =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/forgotpassword/mapper/ForgotPasswordDetailsOtpWebMapper.java");

  @Test
  void detailsMapperExposesNoDownstreamVocabulary() throws Exception {
    String source = Files.readString(MAPPER);
    assertThat(source).doesNotContain("err.tpa.");
    assertThat(source).contains("err.forgot-password.member.notFound");
  }
}

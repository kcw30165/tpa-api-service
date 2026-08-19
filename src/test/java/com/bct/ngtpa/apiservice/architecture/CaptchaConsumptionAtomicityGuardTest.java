package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaptchaConsumptionAtomicityGuardTest {
  @Test
  void consumptionUsesConditionalPersistenceAndNeverMutateExisting() throws Exception {
    String port =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/port/out/CaptchaChallengePort.java"));
    String adapter =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/redis/auth/RedisCaptchaChallengeAdapter.java"));
    String gate =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/service/auth/captcha/RegistrationCaptchaGateService.java"));
    assertThat(port).contains("applyConsumptionAttempt");
    assertThat(adapter).contains("replaceIfUnchanged");
    assertThat(extractMethod(adapter, "markConsumed")).doesNotContain("mutateExisting");
    assertThat(gate).contains("applyConsumptionAttempt");
  }

  private static String extractMethod(String source, String methodName) {
    int start = source.indexOf(methodName + "(");
    assertThat(start).isGreaterThanOrEqualTo(0);
    int brace = source.indexOf('{', start), depth = 0;
    for (int i = brace; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '{') depth++;
      if (c == '}' && --depth == 0) return source.substring(start, i + 1);
    }
    throw new IllegalStateException("method boundary not found");
  }
}

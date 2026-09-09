package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UsernameRecoveryRequestProtectedOperationFailFirstTest {
  private static final Path OPERATION =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/usernamerecovery/UsernameRecoveryRequestProtectedOperation.java");

  @Test
  void requiredOperationExists() {
    assertThat(OPERATION).exists();
  }

  @Test
  void ownsOneOrderedDispatchAndSessionTransitions() throws Exception {
    String source = Files.readString(OPERATION);
    String normalized = normalize(source);

    assertThat(source)
        .contains(
            "UsernameRecoveryRequestProtectedExecutionResult",
            "OriginRefererValidator",
            "CsrfTokenValidationService",
            "context.reserve(command.schemeRef())",
            "context.markRequested(",
            "context.markFailed(",
            "context.markUnknownOutcome(");
    assertThat(normalized)
        .contains(
            "effectiveDestinationPort.resolve(",
            "messageDeliveryPort.deliver(",
            "authFlowSessionPort.updateRevisioned(");

    int reserve = normalized.indexOf("context.reserve(command.schemeRef())");
    int dispatch = normalized.indexOf("messageDeliveryPort.deliver(");
    assertThat(reserve).isGreaterThanOrEqualTo(0).isLessThan(dispatch);
    assertThat(occurrences(normalized, "messageDeliveryPort.deliver(")).isEqualTo(1);
  }

  @Test
  void classifiesFailuresAndOwnsNoLifecycle() throws Exception {
    String source = Files.readString(OPERATION);

    assertThat(source)
        .contains(
            "provenPreDispatch(",
            "possibleSideEffect(",
            "MessageDeliveryStatus.ACCEPTED",
            "MessageDeliveryStatus.FAILED",
            "MessageDeliveryStatus.UNKNOWN_OUTCOME")
        .doesNotContain(
            "UsernameRecoveryIdempotencyPort",
            "IdempotencyExecutor",
            "UsernameRecoveryRequestReplayCodec",
            "recordUnknownOutcome(",
            "org.springframework",
            "Redis");
  }

  @Test
  void productionRemainsLegacy() throws Exception {
    String source =
        Files.readString(
            Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/config/UsernameRecoveryFeatureConfig.java"));

    assertThat(source)
        .contains(
            "return new IdempotentStartUsernameRecoveryService(",
            "return new IdempotentRequestUsernameRecoveryService(",
            "return new CancelUsernameRecoveryService(")
        .doesNotContain("return new RequestUsernameRecoveryService(");
  }

  private static String normalize(String source) {
    return source.replaceAll("\\s+", "");
  }

  private static int occurrences(String source, String token) {
    int count = 0;
    int offset = 0;
    while ((offset = source.indexOf(token, offset)) >= 0) {
      count++;
      offset += token.length();
    }
    return count;
  }
}

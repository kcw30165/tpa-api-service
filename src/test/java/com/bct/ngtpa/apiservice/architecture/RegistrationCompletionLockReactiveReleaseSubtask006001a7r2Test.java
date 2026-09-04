package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationCompletionLockReactiveReleaseSubtask006001a7r2Test {
  private static final Path SERVICE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/complete/idempotency/RegistrationCompleteProtectedOperation.java");

  @Test
  void completionLockCleanupIsComposedWithoutDetachedReactiveTerminalCalls() throws Exception {
    String source = Files.readString(SERVICE);
    assertThat(source).containsIgnoringWhitespaces("Mono.usingWhen(");
    assertThat(source)
        .containsIgnoringWhitespaces("locks.acquire(c.authFlowSessionId(), completionLockLease)");
    assertThat(source).containsIgnoringWhitespaces("locks.release(handle)");
    assertThat(source).containsIgnoringWhitespaces("this::completionLockReleaseAfterSuccess");
    assertThat(source)
        .containsIgnoringWhitespaces("completionLockReleaseAfterError(acquiredHandle)");
    assertThat(source).containsIgnoringWhitespaces("this::completionLockReleaseAfterCancellation");
    assertThat(source)
        .doesNotContain(".subscribe(", ".block(", ".blockOptional(", "toFuture().get(");
    assertThat(source)
        .doesNotContain("doFinally", "doOnSuccess", "doOnError", "doOnCancel", "doAfterTerminate");
  }
}

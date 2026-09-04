package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SharedCompleteRegistrationPrerequisiteTest {
  private static final Path SOURCE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/complete/"
              + "idempotency/SharedCompleteRegistrationService.java");

  @Test
  void protectedContextAndExpiryPrecedeUsernamePasswordAndReservation() throws Exception {
    String source = Files.readString(SOURCE);
    int context = source.indexOf("contexts.load(");
    int expiry = source.indexOf("expiry.refresh(");
    int username = source.indexOf("usernames.load(session)");
    int password = source.indexOf("passwords.validate(");
    int execution = source.indexOf("return execute(command,");
    int executor = source.indexOf("executor.execute(execution)");
    assertThat(context).isGreaterThanOrEqualTo(0);
    assertThat(expiry).isGreaterThan(context);
    assertThat(username).isGreaterThan(expiry);
    assertThat(password).isGreaterThan(username);
    assertThat(execution).isGreaterThan(password);
    assertThat(executor).isGreaterThan(execution);
  }

  @Test
  void invalidPrerequisitesReturnBeforeSharedExecution() throws Exception {
    String source = Files.readString(SOURCE);
    String authorize =
        source.substring(
            source.indexOf("private Mono<CompleteRegistrationResult> authorize"),
            source.indexOf("private Mono<CompleteRegistrationResult> prepare"));
    String prepare =
        source.substring(
            source.indexOf("private Mono<CompleteRegistrationResult> prepare"),
            source.indexOf("private Mono<CompleteRegistrationResult> execute"));
    assertThat(authorize).contains("if (!context.isValid())", "return Mono.just(failure(");
    assertThat(prepare)
        .contains(
            "session.status() != AuthFlowStatus.USERNAME_VERIFIED",
            "if (!validation.valid())",
            "return Mono.just(");
    assertThat(authorize).doesNotContain("executor.execute(");
    assertThat(prepare).doesNotContain("executor.execute(");
  }
}

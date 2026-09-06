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
    String source = Files.readString(SOURCE).replaceAll("\\s+", "");
    int context = source.indexOf("contexts.load(");
    int expiry = source.indexOf("expiry.refresh(");
    int username = source.indexOf("usernames.load(session)");
    int password = source.indexOf("passwords.validate(");
    int execution = source.indexOf("returnexecute(command,");
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
    String source = Files.readString(SOURCE).replaceAll("\\s+", "");
    String authorize =
        source.substring(
            source.indexOf("privateMono<CompleteRegistrationResult>authorize"),
            source.indexOf("privateMono<CompleteRegistrationResult>prepare"));
    String prepare =
        source.substring(
            source.indexOf("privateMono<CompleteRegistrationResult>prepare"),
            source.indexOf("privateMono<CompleteRegistrationResult>execute"));
    assertThat(authorize).contains("if(!context.isValid())", "failure(");
    assertThat(prepare)
        .contains(
            "session.status()!=AuthFlowStatus.USERNAME_VERIFIED",
            "if(!validation.valid())",
            "returnMono.just(");
    assertThat(authorize).doesNotContain("executor.execute(");
    assertThat(prepare).doesNotContain("executor.execute(");
  }
}

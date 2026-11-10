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
  void authorizedStatusControlsNormalExecutionVersusReplayOnly() throws Exception {
    String source = Files.readString(SOURCE).replaceAll("\\s+", "");
    String complete =
        methodBody(
            source,
            "publicMono<CompleteRegistrationResult>complete(CompleteRegistrationCommandcommand)");
    String authorize =
        methodBody(
            source,
            "privateMono<CompleteRegistrationResult>authorize(CompleteRegistrationCommandcommand,"
                + "RegistrationProtectedAuthFlowContextResultcontext)");
    String prepare =
        methodBody(
            source,
            "privateMono<CompleteRegistrationResult>prepare(CompleteRegistrationCommandcommand,"
                + "RegistrationProtectedAuthFlowContextResultcontext,AuthFlowSessionsession,"
                + "booleanreplayOnly)");
    String execute =
        methodBody(
            source,
            "privateMono<CompleteRegistrationResult>execute(CompleteRegistrationCommandcommand,"
                + "RegistrationCompleteProtectedOperationInputinput)");
    String replay =
        methodBody(
            source,
            "privateMono<CompleteRegistrationResult>replay(CompleteRegistrationCommandcommand,"
                + "RegistrationCompleteProtectedOperationInputinput)");

    assertInOrder(complete, "contexts.load(", ".flatMap(context->authorize(command,context))");
    assertThat(complete)
        .doesNotContain("executor.execute(", "executor.replayCompleted(", "operation.execute(");

    assertInOrder(
        authorize,
        "if(!context.isValid())",
        "AuthFlowSessionsession=context.authFlowSession()",
        "if(session==null)",
        "returnswitch(session.status())",
        "caseUSERNAME_VERIFIED->",
        "caseCOMPLETING->",
        "default->");
    String usernameVerifiedBranch =
        sectionBetween(authorize, "caseUSERNAME_VERIFIED->", "caseCOMPLETING->");
    String completingBranch = sectionBetween(authorize, "caseCOMPLETING->", "default->");
    assertThat(usernameVerifiedBranch)
        .contains("expiry.refreshUsernameVerified(command.authFlowSessionId(),clock.instant())")
        .contains(".flatMap(refreshed->prepare(command,context,refreshed,false))");
    assertThat(completingBranch)
        .contains("prepare(command,context,session,true)")
        .doesNotContain(
            "expiry.refreshUsernameVerified", "executor.execute(", "operation.execute(");
    assertThat(authorize)
        .doesNotContain("executor.execute(", "executor.replayCompleted(", "operation.execute(");

    assertInOrder(
        prepare,
        "usernames.load(session)",
        "passwords.validate(command.password(),command.confirmPassword(),username.username())",
        "if(!validation.valid())",
        "newRegistrationCompleteProtectedOperationInput(",
        "returnreplayOnly?replay(command,input):execute(command,input)");
    assertThat(prepare)
        .doesNotContain("executor.execute(", "executor.replayCompleted(", "operation.execute(");
    assertThat(execute).contains("executor.execute(execution(command,input))");
    assertThat(replay)
        .contains(
            "executor.replayCompleted(execution.operationId(),execution.fingerprint(),"
                + "execution.replayResolver())")
        .doesNotContain("executor.execute(", "operation.execute(");
  }

  @Test
  void invalidPrerequisitesReturnBeforeSharedExecution() throws Exception {
    String source = Files.readString(SOURCE).replaceAll("\\s+", "");
    String authorize =
        methodBody(
            source,
            "privateMono<CompleteRegistrationResult>authorize(CompleteRegistrationCommandcommand,"
                + "RegistrationProtectedAuthFlowContextResultcontext)");
    String prepare =
        methodBody(
            source,
            "privateMono<CompleteRegistrationResult>prepare(CompleteRegistrationCommandcommand,"
                + "RegistrationProtectedAuthFlowContextResultcontext,AuthFlowSessionsession,"
                + "booleanreplayOnly)");

    String invalidContext = sectionBetween(authorize, "if(!context.isValid()){", "AuthFlowSession");
    String missingSession = sectionBetween(authorize, "if(session==null){", "returnswitch");
    String invalidStatus = sectionBetween(authorize, "default->", "};");
    assertThat(invalidContext)
        .contains("returnMono.just(failure(")
        .doesNotContain("prepare(", "executor.", "operation.execute(");
    assertThat(missingSession)
        .contains("returnMono.just(failure(")
        .doesNotContain("prepare(", "executor.", "operation.execute(");
    assertThat(invalidStatus)
        .contains("CompleteRegistrationResultStatus.INVALID_STATE", "failure(")
        .doesNotContain("prepare(", "executor.", "operation.execute(");

    String usernameFailure =
        sectionBetween(prepare, "catch(RuntimeExceptionexception){", "varvalidation=");
    assertThat(usernameFailure)
        .contains("returnMono.just(failure(")
        .doesNotContain("executor.", "operation.execute(", "replay(", "execute(");
    String invalidPassword =
        sectionBetween(
            prepare,
            "if(!validation.valid()){",
            "RegistrationCompleteProtectedOperationInputinput=");
    assertThat(invalidPassword)
        .contains("CompleteRegistrationResultStatus.VALIDATION_FAILED", "returnMono.just(")
        .doesNotContain("executor.", "operation.execute(", "replay(", "execute(");
  }

  private static String methodBody(String source, String signature) {
    int start = source.indexOf(signature);
    assertThat(start).as("method signature: %s", signature).isGreaterThanOrEqualTo(0);
    int openingBrace = source.indexOf('{', start + signature.length());
    assertThat(openingBrace).as("method body: %s", signature).isGreaterThanOrEqualTo(0);

    int depth = 0;
    for (int index = openingBrace; index < source.length(); index++) {
      char current = source.charAt(index);
      if (current == '{') {
        depth++;
      } else if (current == '}') {
        depth--;
        if (depth == 0) {
          return source.substring(openingBrace + 1, index);
        }
      }
    }
    throw new AssertionError("method body did not close: " + signature);
  }

  private static String sectionBetween(String source, String startMarker, String endMarker) {
    int start = source.indexOf(startMarker);
    assertThat(start).as("start marker: %s", startMarker).isGreaterThanOrEqualTo(0);
    int sectionStart = start + startMarker.length();
    int end = source.indexOf(endMarker, sectionStart);
    assertThat(end).as("end marker: %s", endMarker).isGreaterThanOrEqualTo(sectionStart);
    return source.substring(sectionStart, end);
  }

  private static void assertInOrder(String source, String... fragments) {
    int previous = -1;
    for (String fragment : fragments) {
      int next = source.indexOf(fragment, previous + 1);
      assertThat(next).as("missing or out-of-order fragment: %s", fragment).isGreaterThan(previous);
      previous = next;
    }
  }
}

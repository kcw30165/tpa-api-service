package com.bct.ngtpa.apiservice.application.usecase.auth.registration.complete.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SharedCompleteRegistrationServiceArchitectureTest {
  private static final Path SERVICE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/complete/"
              + "idempotency/SharedCompleteRegistrationService.java");

  @Test
  void prerequisitesPrecedeSharedExecution() throws Exception {
    String source = Files.readString(SERVICE).replaceAll("\\s+", "");
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

  @Test
  void completeUsesOpaqueSharedIdentityAndPasswordDigestFingerprint() throws Exception {
    String source = Files.readString(SERVICE).replaceAll("\\s+", "");
    assertThat(source)
        .contains("RegistrationSharedIdempotencyAction.COMPLETE")
        .contains("command.idempotencyKey().value(),sessionScope")
        .contains("List.of(sessionScope,sha256(command.password()))")
        .doesNotContain("RegistrationIdempotencyPort", "RegistrationRequestHashGenerator");
  }

  @Test
  void classifierPreservesPreDispatchAndAmbiguousBoundary() throws Exception {
    String source = Files.readString(SERVICE).replaceAll("\\s+", "");
    assertThat(source)
        .contains("failureinstanceofRegistrationCompletionPreDispatchException")
        .contains("IdempotencyFailureClassification.PROVEN_NO_SIDE_EFFECT")
        .contains("IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT");
  }

  @Test
  void replayReturnsOriginalTypedResultAndLifecycleFailuresRemainCanonical() throws Exception {
    String source = Files.readString(SERVICE).replaceAll("\\s+", "");
    assertThat(source)
        .contains("caseACQUIRED,COMPLETED_REPLAY->responses.fromTransientResponse(response)")
        .contains("caseFAILED_EXISTING")
        .contains("caseUNKNOWN_OUTCOME_EXISTING")
        .contains("REGISTRATION_COMPLETE_OUTCOMEUNKNOWN")
        .doesNotContain("CompleteRegistrationResultStatus.COMPLETED_REPLAY,");
  }
}

package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Executable preservation guards for the audited COMPLETE protected-operation behavior matrix. */
class ForgotPasswordCompleteProtectedOperationBehaviorGuardTest {
  private static final Path OPERATION =
      sourcePath(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/complete/ForgotPasswordCompleteProtectedOperation.java");

  @Test
  void successConsumesAuthorizationAndCompletesTheSession() throws Exception {
    String source = Files.readString(OPERATION);
    assertThat(source)
        .contains(
            "ForgotPasswordCompletionResult.Status.RESET",
            "authorization.expiresAt()",
            "ForgotPasswordResetAuthorization consumed",
            "withResetAuthorization(consumed)",
            "AuthFlowStatus.COMPLETING",
            "AuthFlowStatus.COMPLETED",
            "WorkflowNextStep.RETURN_TO_LOGIN");
  }

  @Test
  void recognizedBusinessRejectionsPreserveDistinctTerminalBehavior() throws Exception {
    String source = Files.readString(OPERATION);
    assertThat(source)
        .contains(
            "ForgotPasswordCompleteErrorKeys.PASSWORD_INVALID",
            "passwordInvalid()",
            "ForgotPasswordCompleteErrorKeys.MEMBER_NOT_FOUND",
            "memberNotFound()",
            "WorkflowNextStep.PASSWORD_SETUP",
            "WorkflowNextStep.RETURN_TO_LOGIN",
            "CompleteForgotPasswordStatus.BUSINESS_REJECTED");
  }

  @Test
  void uncertainDispatchAndTerminalMutationBlockTheSession() throws Exception {
    String source = Files.readString(OPERATION);
    String invoke =
        section(
            source,
            "private Mono<ForgotPasswordCompleteProtectedExecutionResult> invoke(",
            "private Mono<ForgotPasswordCompleteProtectedExecutionResult> classify(");
    String classify =
        section(
            source,
            "private Mono<ForgotPasswordCompleteProtectedExecutionResult> classify(",
            "private Mono<ForgotPasswordCompleteProtectedExecutionResult> blockUnknown(");
    String blockUnknown =
        section(
            source,
            "private Mono<ForgotPasswordCompleteProtectedExecutionResult> blockUnknown(",
            "private ForgotPasswordCompleteProtectedExecutionResult technical(");

    String compactInvoke = compact(invoke);
    String compactClassify = compact(classify);
    String compactBlockUnknown = compact(blockUnknown);

    assertThat(compactInvoke)
        .contains(
            "returncompletion.complete(",
            ".flatMap(result->classify(session,context,result,now))",
            ".onErrorResume(failure->blockUnknown(session,now,List.of(incidentFor(failure))))");
    assertThat(count(compactInvoke, "completion.complete(")).isEqualTo(1);

    assertThat(compactClassify)
        .contains(
            "if(result.status()==ForgotPasswordCompletionResult.Status.UNKNOWN_OUTCOME){returnblockUnknown(session,now,List.of(incidentFor(result)));}",
            "updateSession(session,AuthFlowStatus.COMPLETED,context,now).thenReturn(ForgotPasswordCompleteProtectedExecutionResult.terminal(memberNotFound())).onErrorResume(failure->blockUnknown(session,now,List.of(ForgotPasswordCompleteIncident.local(\"COMPLETE_SESSION_UPDATE_FAILURE\",\"session-update\"))))",
            "updateStatus(session,AuthFlowStatus.COMPLETING,now).flatMap(completing->updateSession(completing,AuthFlowStatus.COMPLETED,updatedContext,now)).thenReturn(ForgotPasswordCompleteProtectedExecutionResult.terminal(success())).onErrorResume(failure->blockUnknown(session,now,List.of(ForgotPasswordCompleteIncident.local(\"COMPLETE_SESSION_UPDATE_FAILURE\",\"session-update\"))))");
    assertThat(count(compactClassify, "blockUnknown(")).isEqualTo(3);

    assertThat(compactBlockUnknown)
        .contains(
            "AuthFlowSessionsession,Instantnow,List<ForgotPasswordCompleteIncident>incidents",
            "updateStatus(session,AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED,now)",
            "COMPLETE_BLOCKED_SESSION_WRITE_FAILURE",
            "append(incidents,ForgotPasswordCompleteIncident.local(\"COMPLETE_BLOCKED_SESSION_WRITE_FAILURE\",\"blocked-session-write\"))",
            ".thenReturn(incidents)",
            "ForgotPasswordCompleteProtectedExecutionResult.unknownOutcome(unknownOutcome(),failures)");
    assertThat(
            count(
                compactBlockUnknown,
                "updateStatus(session,AuthFlowStatus.UNKNOWN_OUTCOME_BLOCKED,now)"))
        .isEqualTo(1);
  }

  @Test
  void lockAndTrustedActorRoleStayInsideTheProtectedBoundary() throws Exception {
    String source = Files.readString(OPERATION);
    assertThat(source)
        .contains(
            "return locks",
            ".acquire(input.command().authFlowSessionId(), lockLease)",
            "Mono.usingWhen(",
            "command.actorUserRole()",
            "releaseAfterSuccess",
            "releaseAfterFailure",
            "releaseAfterCancellation");
  }

  @Test
  void acquisitionAndPostAcquisitionFailuresHaveSeparateClassifications() throws Exception {
    String source = Files.readString(OPERATION);
    String execute =
        section(
            source,
            "public Mono<ForgotPasswordCompleteProtectedExecutionResult> execute(",
            "private Mono<ForgotPasswordCompleteProtectedExecutionResult> route(");
    String compact = compact(execute);

    assertThat(compact)
        .contains(
            "Mono.defer(()->locks.acquire(input.command().authFlowSessionId(),lockLease))",
            "COMPLETE_LOCK_ACQUISITION_FAILURE",
            "\"lock-acquisition\"",
            "Mono.defer(()->route(input,handle))",
            "COMPLETE_PROTECTED_OPERATION_FAILURE",
            "\"protected-operation\"");
  }

  @Test
  void protectedBoundaryOwnsNoIdempotencyLifecycleOrSensitiveLogging() throws Exception {
    assertThat(Files.readString(OPERATION))
        .doesNotContain(
            "IdempotencyExecutor",
            "ForgotPasswordIdempotencyPort",
            "markCompleted(",
            "markFailed(",
            "requestHash(",
            ".block(",
            ".subscribe(",
            "System.out",
            "logger.info",
            "logger.debug");
  }

  private static String section(String source, String start, String end) {
    int startIndex = source.indexOf(start);
    int endIndex = source.indexOf(end, startIndex);
    if (startIndex < 0 || endIndex < 0) {
      throw new AssertionError("Could not locate source section: " + start);
    }
    return source.substring(startIndex, endIndex);
  }

  private static Path sourcePath(String relativePath) {
    String root = System.getProperty("guard.source.root");
    return root == null ? Path.of(relativePath) : Path.of(root, relativePath);
  }

  private static String compact(String source) {
    return source.replaceAll("\\s+", "");
  }

  private static int count(String source, String value) {
    int count = 0;
    int offset = 0;
    while ((offset = source.indexOf(value, offset)) >= 0) {
      count++;
      offset += value.length();
    }
    return count;
  }
}

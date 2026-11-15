package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Fail-first contract for COMPLETE integration over the repository-wide shared executor. */
class ForgotPasswordCompleteSharedCoordinatorFailFirstTest {
  private static final Path COORDINATOR =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/complete/IdempotentCompleteForgotPasswordService.java");
  private static final Path SHARED_EXECUTOR =
      Path.of("src/main/java/com/bct/ngtpa/apiservice/application/idempotency/DefaultIdempotencyExecutor.java");

  @Test
  void requiresSharedCoordinatorImplementation() {
    assertThat(COORDINATOR).exists();
  }

  @Test
  void coordinatorUsesConstantFingerprintSessionScopeAndPossibleSideEffectClassifier()
      throws Exception {
    if (!Files.exists(COORDINATOR)) return;
    assertThat(Files.readString(COORDINATOR))
        .contains(
            "implements CompleteForgotPasswordUseCase",
            "IdempotencyExecutor",
            "IdempotentExecutionCommand",
            "ForgotPasswordIdempotencyAction.COMPLETE",
            "command.authFlowSessionId().value()",
            "command.idempotencyKey().value()",
            "policy.complete()",
            "command.idempotencyTtl()",
            "IdempotencyFailureClassifiers.possibleSideEffect()",
            "responses::capture",
            "responses::hydrate");
  }

  @Test
  void acquiredExecutionRoutesClassifiedProtectedResultsBeforeCapture() throws Exception {
    if (!Files.exists(COORDINATOR)) return;
    String source = Files.readString(COORDINATOR);
    String executionSetup =
        section(
            source,
            "var execution =",
            "return Mono.defer(() -> executor.execute(execution))");
    String acquired =
        section(
            source,
            "private Mono<HydratedIdempotentResponse> executeAcquired(",
            "private Mono<HydratedIdempotentResponse> executeLoaded(");
    String loaded =
        section(
            source,
            "private Mono<HydratedIdempotentResponse> executeLoaded(",
            "private ForgotPasswordAuthFlowContext loadContext(");
    String outcomeRoute =
        section(
            source,
            "private CompleteForgotPasswordResult route(",
            "private CompleteForgotPasswordResult invalidAuthFlow(");
    String executorSource = Files.readString(SHARED_EXECUTOR);
    String executorRoute =
        section(
            executorSource,
            "private Mono<IdempotentExecutionResult> route(",
            "private Mono<IdempotentExecutionResult> hydrateCompleted(");
    String executorCapture =
        section(
            executorSource,
            "private Mono<IdempotentExecutionResult> complete(",
            "private Mono<HydratedIdempotentResponse> classifyProtectedFailureThenError(");

    assertThat(compact(executionSetup))
        .contains(
            "() -> executeAcquired(",
            "IdempotencyFailureClassifiers.possibleSideEffect()",
            "responses::capture",
            "responses::hydrate");
    assertThat(compact(acquired)).contains("snapshot -> executeLoaded(");
    assertThat(loaded)
        .contains(
            "operation.execute(",
            "ForgotPasswordCompleteProtectedExecutionResult.Outcome.TERMINAL",
            "ForgotPasswordCompleteProtectedExecutionResult.Outcome.TECHNICAL_FAILURE",
            "execution.incidents()",
            "responses.toTransientResponse(execution.result())",
            "Mono.error(signal)");
    assertThat(compact(loaded))
        .contains(
            "if (execution.outcome() == ForgotPasswordCompleteProtectedExecutionResult.Outcome.TERMINAL) { return Mono.just(responses.toTransientResponse(execution.result())); }");
    assertThat(count(loaded, "operation.execute(")).isEqualTo(1);
    assertThat(outcomeRoute)
        .contains(
            "case ACQUIRED, COMPLETED_REPLAY -> responses.fromTransientResponse(response)",
            "case UNKNOWN_OUTCOME_EXISTING",
            "unknownOutcome()");
    assertThat(outcomeRoute)
        .doesNotContain("executeAcquired(", "executeLoaded(", "operation.execute(");
    assertThat(compact(executorRoute))
        .contains(
            "case ACQUIRED -> executeOwned(command, reservation.reservationToken())",
            "case COMPLETED_REPLAY -> hydrateCompleted(reservation.terminalResponse(), command.replayResolver())",
            "case DUPLICATE_IN_PROGRESS, KEY_REUSED_WITH_DIFFERENT_REQUEST, FAILED_EXISTING, UNKNOWN_OUTCOME_EXISTING -> Mono.just(");
    assertThat(compact(executorCapture))
        .contains(
            "Mono.defer(() -> command.responseCapture().capture(response))",
            "persistence .complete(");
  }

  @Test
  void lifecycleOutcomesPreserveExistingPublicStatusesWithoutProtectedExecution() throws Exception {
    if (!Files.exists(COORDINATOR)) return;
    assertThat(Files.readString(COORDINATOR))
        .contains(
            "case ACQUIRED, COMPLETED_REPLAY",
            "case DUPLICATE_IN_PROGRESS",
            "CompleteForgotPasswordStatus.DUPLICATE_IN_PROGRESS",
            "case KEY_REUSED_WITH_DIFFERENT_REQUEST",
            "CompleteForgotPasswordStatus.IDEMPOTENCY_KEY_REUSED",
            "case FAILED_EXISTING",
            "CompleteForgotPasswordStatus.SYSTEM_FAILED",
            "case UNKNOWN_OUTCOME_EXISTING",
            "CompleteForgotPasswordStatus.UNKNOWN_OUTCOME");
  }

  @Test
  void coordinatorOwnsNoLegacyIdempotencyPersistenceOrSensitiveFingerprintInput() throws Exception {
    if (!Files.exists(COORDINATOR)) return;
    assertThat(Files.readString(COORDINATOR))
        .doesNotContain(
            "ForgotPasswordIdempotencyPort",
            "startProcessing(",
            "markCompleted(",
            "markFailed(",
            "markUnknownOutcome(",
            "command.password()",
            "command.confirmPassword()",
            "command.csrfToken()",
            ".block(",
            ".subscribe(");
  }

  private static String section(String source, String start, String end) {
    int startIndex = source.indexOf(start);
    int endIndex = source.indexOf(end, startIndex);
    if (startIndex < 0 || endIndex < 0) {
      throw new AssertionError("Could not locate source section: " + start);
    }
    return source.substring(startIndex, endIndex);
  }

  private static String compact(String source) {
    return source.replaceAll("\\s+", " ").trim();
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

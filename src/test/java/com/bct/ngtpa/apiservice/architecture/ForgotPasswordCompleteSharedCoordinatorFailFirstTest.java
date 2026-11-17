package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Fail-first contract for COMPLETE integration over the repository-wide shared executor. */
class ForgotPasswordCompleteSharedCoordinatorFailFirstTest {
  private static final Path COORDINATOR =
      sourcePath(
          "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/complete/IdempotentCompleteForgotPasswordService.java");
  private static final Path SHARED_EXECUTOR =
      sourcePath(
          "src/main/java/com/bct/ngtpa/apiservice/application/idempotency/DefaultIdempotencyExecutor.java");

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
    String publicComplete =
        section(
            source,
            "public Mono<CompleteForgotPasswordResult> complete(",
            "private Mono<HydratedIdempotentResponse> executeAcquired(");
    String executionSetup =
        section(source, "var execution =", "return Mono.defer(() -> executor.execute(execution))");
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
    String serviceRoute =
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
    String executorProtected =
        section(
            executorSource,
            "private Mono<HydratedIdempotentResponse> executeProtected(",
            "private Mono<IdempotentExecutionResult> complete(");
    String executorClassification =
        section(
            executorSource,
            "private Mono<HydratedIdempotentResponse> classifyProtectedFailureThenError(",
            "private <T> Mono<T> markUnknownThenError(");
    String unknownPersistence =
        section(executorSource, "private <T> Mono<T> markUnknownThenError(", "\n}");
    String hydrateCompleted =
        section(
            executorSource,
            "private Mono<IdempotentExecutionResult> hydrateCompleted(",
            "private Mono<IdempotentExecutionResult> executeOwned(");
    String unknownReplay = section(executorRoute, "case DUPLICATE_IN_PROGRESS,", "};");

    assertThat(compact(publicComplete))
        .contains("()->executeAcquired(", "returnMono.defer(()->executor.execute(execution))");
    assertThat(count(publicComplete, "executor.execute(execution)")).isEqualTo(1);
    assertThat(publicComplete).doesNotContain("operation.execute(");
    assertThat(compact(executionSetup))
        .contains(
            "()->executeAcquired(",
            "IdempotencyFailureClassifiers.possibleSideEffect()",
            "responses::capture",
            "responses::hydrate");
    assertThat(count(compact(executionSetup), "IdempotencyFailureClassifiers.possibleSideEffect()"))
        .isEqualTo(1);
    assertThat(compact(acquired)).contains("snapshot->executeLoaded(");
    assertThat(count(compact(acquired), "executeLoaded(")).isEqualTo(1);
    assertThat(acquired).doesNotContain("operation.execute(");
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
            "if(execution.outcome()==ForgotPasswordCompleteProtectedExecutionResult.Outcome.TERMINAL){returnMono.just(responses.toTransientResponse(execution.result()));}");
    assertThat(count(loaded, "operation.execute(")).isEqualTo(1);
    assertThat(serviceRoute)
        .contains(
            "case ACQUIRED, COMPLETED_REPLAY -> responses.fromTransientResponse(response)",
            "case UNKNOWN_OUTCOME_EXISTING",
            "unknownOutcome()")
        .doesNotContain("executeAcquired(", "executeLoaded(", "operation.execute(");
    assertThat(compact(executorRoute))
        .contains(
            "caseACQUIRED->executeOwned(command,reservation.reservationToken())",
            "caseCOMPLETED_REPLAY->hydrateCompleted(reservation.terminalResponse(),command.replayResolver())",
            "caseDUPLICATE_IN_PROGRESS,KEY_REUSED_WITH_DIFFERENT_REQUEST,FAILED_EXISTING,UNKNOWN_OUTCOME_EXISTING->Mono.just(");
    assertThat(compact(executorProtected))
        .contains(
            ".onErrorResume(failure->classifyProtectedFailureThenError(command,owner,failure))");
    assertThat(compact(executorClassification))
        .contains(
            "classification=command.failureClassifier().classify(failure);",
            "classification==null||classification==IdempotencyFailureClassification.POSSIBLE_SIDE_EFFECT){returnmarkUnknownThenError(command,owner,failure);}");
    assertThat(compact(unknownPersistence))
        .contains("persistence.markUnknown(", "newIdempotencyCommands.MarkUnknown(");
    assertThat(compact(hydrateCompleted))
        .contains(".hydrate(snapshot)")
        .doesNotContain("executeOwned(", "executeProtected(", "operation.execute(");
    assertThat(compact(unknownReplay))
        .contains(
            "caseDUPLICATE_IN_PROGRESS,KEY_REUSED_WITH_DIFFERENT_REQUEST,FAILED_EXISTING,UNKNOWN_OUTCOME_EXISTING->Mono.just(newIdempotentExecutionResult(reservation.outcome(),null));")
        .doesNotContain("executeOwned(", "executeProtected(", "operation.execute(");
    assertThat(compact(executorCapture))
        .contains(
            "Mono.defer(()->command.responseCapture().capture(response))", "persistence.complete(");
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

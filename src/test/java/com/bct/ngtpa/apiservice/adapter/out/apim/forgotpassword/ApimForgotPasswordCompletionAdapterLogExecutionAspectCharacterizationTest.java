package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.port.out.ForgotPasswordCompletionPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionResult;
import com.bct.ngtpa.apiservice.infrastructure.logging.ExecutionLoggingAspect;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.bct.ngtpa.apiservice.shared.masking.MaskingProperties;
import com.bct.ngtpa.apiservice.shared.masking.MaskingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Sub-task 2b.2 STEP 1, Batch 2a (Forgot Password Completion), Test A (aspect-weaving): real {@link
 * ApimForgotPasswordCompletionAdapter} wired through a real {@link ExecutionLoggingAspect} via
 * {@link AspectJProxyFactory}, stubbing only {@link ApimExchangeExecutor}. The adapter itself
 * writes no log line (no {@code @Slf4j}/log field) - the {@code method.execution.*} lines pinned
 * here come entirely from {@code @LogExecution(value = "apim.trpResetPassword", logArgs = false)}
 * being woven by {@link ExecutionLoggingAspect}'s annotation-based pointcut. The {@link
 * ListAppender} is therefore attached to {@code ExecutionLoggingAspect}'s own logger, not the
 * adapter's (precedent: {@code ExecutionLoggingAspectTest.proxiedService()}, lines 336-343).
 *
 * <p>This class is distinct from the pre-existing {@code
 * ApimForgotPasswordCompletionAdapterBatch2aCharacterizationTest}, which stubs {@link
 * ApimExchangeExecutor} against the unproxied adapter directly and characterizes result/exception
 * shape only (not logging); that file is unmodified by this test.
 */
class ApimForgotPasswordCompletionAdapterLogExecutionAspectCharacterizationTest {

  private Logger aspectLogger;
  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void setUp() {
    listAppender = new ListAppender<>();
    listAppender.start();
    aspectLogger = (Logger) LoggerFactory.getLogger(ExecutionLoggingAspect.class);
    aspectLogger.addAppender(listAppender);
  }

  @AfterEach
  void tearDown() {
    aspectLogger.detachAppender(listAppender);
    listAppender.stop();
  }

  private ForgotPasswordCompletionPort proxiedAdapter(ApimExchangeExecutor executor) {
    ApimForgotPasswordCompletionAdapter target = new ApimForgotPasswordCompletionAdapter(executor);
    AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
    ObjectMapper objectMapper = new ObjectMapper();
    proxyFactory.addAspect(
        new ExecutionLoggingAspect(
            new LoggingSanitizer(objectMapper, new MaskingService(new MaskingProperties())),
            objectMapper));
    // AspectJProxyFactory defaults to a JDK dynamic proxy when the target implements an
    // interface, so the proxy only implements ForgotPasswordCompletionPort and cannot be cast
    // back to the concrete ApimForgotPasswordCompletionAdapter class. Only complete(...),
    // declared on the interface, is ever invoked on the returned proxy.
    return proxyFactory.getProxy();
  }

  /**
   * Scenario 1 of 3: the downstream envelope is missing entirely (empty {@code data} array). Per
   * the confirmed adapter mechanism, {@code toEnvelope()} throws an {@code ApimException}
   * synchronously inside {@code .map(...)}, which becomes an {@code onError} signal. {@code
   * ExecutionLoggingAspect.decorateMono(...)} routes {@code onError} to {@code logError(...)}, so
   * exactly {@code method.execution.start} (INFO) then {@code method.execution.error} (ERROR, with
   * stack trace) are logged.
   */
  @Test
  void missingEnvelopeLogsStartThenErrorWithStackTrace() {
    // envelope.getResponse() must itself be null to trigger the adapter's own
    // ApimException throw inside toEnvelope() (confirmed:
    // "envelope == null || envelope.getResponse() == null"). An envelope with a
    // non-null body but an empty data list does NOT throw - it reaches
    // resultMapper.toResult(...) and produces a normal TECHNICAL_FAILURE result
    // instead (onNext, not onError), as confirmed by the first failed run of
    // this scenario.
    ApimExchangeExecutor executor =
        new ApimExchangeExecutor() {
          @Override
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
              ApimExchangeRequest<R, D> request) {
            ApimResponseEnvelope<D> envelope = new ApimResponseEnvelope<>(null);
            return Mono.just(envelope);
          }
        };
    ForgotPasswordCompletionPort adapter = proxiedAdapter(executor);
    var command =
        new ForgotPasswordCompletionCommand(
            "afs-aspect-missing-envelope", "member1", "Password1!", ActorUserRole.ROLE_USER_MBR);

    StepVerifier.create(adapter.complete(command))
        .expectErrorSatisfies(
            e -> assertThat(e).isInstanceOf(com.bct.ngtpa.apiservice.exception.ApimException.class))
        .verify();

    assertThat(listAppender.list).hasSize(2);
    assertThat(listAppender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    assertThat(listAppender.list.get(0).getFormattedMessage()).contains("method.execution.start");
    assertThat(listAppender.list.get(1).getLevel()).isEqualTo(Level.ERROR);
    assertThat(listAppender.list.get(1).getFormattedMessage()).contains("method.execution.error");
    assertThat(listAppender.list.get(1).getThrowableProxy()).isNotNull();
  }

  /**
   * Scenario 2 of 3: the executor's {@code postEnvelope(...)} itself fails with an unexpected,
   * non-crypto error (e.g. transport failure). {@code onErrorResume} only intercepts crypto
   * failures, so this propagates to {@code onErrorMap}'s {@code safeFailure}, which wraps it as an
   * {@code ApimException} - still an {@code onError} signal. Same log shape as scenario 1: {@code
   * method.execution.start} then {@code method.execution.error} with stack trace.
   */
  @Test
  void unexpectedFailureLogsStartThenErrorWithStackTrace() {
    ApimExchangeExecutor executor =
        new ApimExchangeExecutor() {
          @Override
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
              ApimExchangeRequest<R, D> request) {
            return Mono.error(new IllegalStateException("transport boom"));
          }
        };
    ForgotPasswordCompletionPort adapter = proxiedAdapter(executor);
    var command =
        new ForgotPasswordCompletionCommand(
            "afs-aspect-unexpected-failure", "member1", "Password1!", ActorUserRole.ROLE_USER_MBR);

    StepVerifier.create(adapter.complete(command)).expectError().verify();

    assertThat(listAppender.list).hasSize(2);
    assertThat(listAppender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    assertThat(listAppender.list.get(0).getFormattedMessage()).contains("method.execution.start");
    assertThat(listAppender.list.get(1).getLevel()).isEqualTo(Level.ERROR);
    assertThat(listAppender.list.get(1).getFormattedMessage()).contains("method.execution.error");
    assertThat(listAppender.list.get(1).getThrowableProxy()).isNotNull();
  }

  /**
   * Scenario 3 of 3: the executor's {@code postEnvelope(...)} fails with {@link
   * ApimCryptoException}. {@code onErrorResume(ApimCryptoFailureClassifier::isCryptoFailure, ...)}
   * intercepts it before {@code onErrorMap} and resolves to {@code
   * Mono.just(ForgotPasswordCompletionResult.unknownOutcome())} - a SUCCESS signal, not an error
   * signal. {@code ExecutionLoggingAspect.decorateMono(...)} therefore logs {@code
   * method.execution.start} then {@code method.execution.success}, with NO {@code
   * method.execution.error} at all.
   */
  @Test
  void cryptoFailureLogsStartThenSuccessWithNoError() {
    ApimExchangeExecutor executor =
        new ApimExchangeExecutor() {
          @Override
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
              ApimExchangeRequest<R, D> request) {
            return Mono.error(new ApimCryptoException("Failed to decrypt APIM response payload."));
          }
        };
    ForgotPasswordCompletionPort adapter = proxiedAdapter(executor);
    var command =
        new ForgotPasswordCompletionCommand(
            "afs-aspect-crypto-failure", "member1", "Password1!", ActorUserRole.ROLE_USER_MBR);

    ForgotPasswordCompletionResult result = adapter.complete(command).block();
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(ForgotPasswordCompletionResult.Status.UNKNOWN_OUTCOME);

    assertThat(listAppender.list).hasSize(2);
    assertThat(listAppender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    assertThat(listAppender.list.get(0).getFormattedMessage()).contains("method.execution.start");
    assertThat(listAppender.list.get(1).getLevel()).isEqualTo(Level.INFO);
    assertThat(listAppender.list.get(1).getFormattedMessage()).contains("method.execution.success");
    assertThat(listAppender.list)
        .noneMatch(event -> event.getFormattedMessage().contains("method.execution.error"));
  }

  /**
   * 4th scenario (Batch 2a follow-up): the downstream envelope is present with an empty {@code
   * err-message} and empty {@code data}. Per
   * ApimForgotPasswordCompletionAdapterBatch2aCharacterizationTest.
   * complete_emptyButPresentResponse_returnsTechnicalFailureResult, this does not throw - {@code
   * toEnvelope()} passes the envelope through unchanged and {@code resultMapper.toResult(...)} maps
   * it to a normal {@link ForgotPasswordCompletionResult} with status {@code TECHNICAL_FAILURE}.
   * Since the @LogExecution-annotated complete(...) method's own Mono completes with {@code onNext}
   * (a result value), not {@code onError}, the aspect logs method.execution.start then
   * method.execution.success (INFO), with no method.execution.error.
   */
  @Test
  void emptyButPresentResponseLogsStartThenSuccess() {
    ApimExchangeExecutor executor =
        new ApimExchangeExecutor() {
          @Override
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
              ApimExchangeRequest<R, D> request) {
            ApimResponseBody<D> body = new ApimResponseBody<>("", List.of());
            ApimResponseEnvelope<D> envelope = new ApimResponseEnvelope<>(body);
            return Mono.just(envelope);
          }
        };
    ForgotPasswordCompletionPort adapter = proxiedAdapter(executor);
    var command =
        new ForgotPasswordCompletionCommand(
            "afs-aspect-empty-but-present", "member1", "Password1!", ActorUserRole.ROLE_USER_MBR);

    ForgotPasswordCompletionResult result = adapter.complete(command).block();
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(ForgotPasswordCompletionResult.Status.TECHNICAL_FAILURE);

    assertThat(listAppender.list).hasSize(2);
    assertThat(listAppender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    assertThat(listAppender.list.get(0).getFormattedMessage()).contains("method.execution.start");
    assertThat(listAppender.list.get(1).getLevel()).isEqualTo(Level.INFO);
    assertThat(listAppender.list.get(1).getFormattedMessage()).contains("method.execution.success");
    assertThat(listAppender.list)
        .noneMatch(event -> event.getFormattedMessage().contains("method.execution.error"));
  }
}

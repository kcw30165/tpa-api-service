package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimIncidentOwnership;
import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.ForgotPasswordUsernameLookupStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.start.StartForgotPasswordUsernameLookupCommand;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Batch 2a follow-up: {@link ApimForgotPasswordUsernameLookupAdapter}, stubbing only {@link
 * ApimExchangeExecutor}, mirroring the style of {@code
 * ApimForgotPasswordCompletionAdapterBatch2aCharacterizationTest}.
 *
 * <p>Scenario 1: missing response envelope. The adapter classifies the contract failure and returns
 * {@code SYSTEM_FAILED} to the protected-operation terminal owner.
 *
 * <p>Scenario 2: malformed envelope. The adapter retains malformed-response detection, classifies
 * the contract failure, and returns {@code SYSTEM_FAILED}; its lower observation remains WARN-only.
 */
class ApimForgotPasswordUsernameLookupAdapterBatch2aCharacterizationTest {
  private static final String RAW_MARKER = "RAW_DOWNSTREAM_DIAGNOSTIC_MARKER";

  @Test
  void lookup_missingEnvelope_returnsClassifiedSystemFailureWithWarnOnlyObservation() {
    var capturedRequest = new AtomicReference<ApimExchangeRequest<?, ?>>();
    ApimExchangeExecutor executor =
        new ApimExchangeExecutor() {
          @Override
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
              ApimExchangeRequest<R, D> request) {
            capturedRequest.set(request);
            ApimResponseEnvelope<D> envelope = new ApimResponseEnvelope<>(null);
            return Mono.just(envelope);
          }
        };
    var adapter = new ApimForgotPasswordUsernameLookupAdapter(executor, Map.of());

    var command =
        new StartForgotPasswordUsernameLookupCommand(
            "normalizedUsername-value", "correlationId-value", ActorUserRole.ROLE_INPUT);
    assertSystemFailedWithWarnOnlyObservation(adapter, command);
    assertThat(capturedRequest.get()).isNotNull();
    assertThat(capturedRequest.get().incidentOwnership())
        .isEqualTo(ApimIncidentOwnership.TERMINAL_CALLER);
  }

  @Test
  void lookup_malformedDataReturnsClassifiedSystemFailureWithWarnOnlyObservation() {
    ApimExchangeExecutor executor =
        new ApimExchangeExecutor() {
          @Override
          public <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(
              ApimExchangeRequest<R, D> request) {
            D malformedItem =
                request
                    .responseDataType()
                    .cast(new TrpChkCustIdApimDataItem(null, null, null, RAW_MARKER, null));
            ApimResponseBody<D> body = new ApimResponseBody<>("", List.of(malformedItem));
            ApimResponseEnvelope<D> envelope = new ApimResponseEnvelope<>(body);
            return Mono.just(envelope);
          }
        };
    var adapter = new ApimForgotPasswordUsernameLookupAdapter(executor, Map.of());

    var command =
        new StartForgotPasswordUsernameLookupCommand(
            "normalizedUsername-value", "correlationId-value", ActorUserRole.ROLE_INPUT);
    assertSystemFailedWithWarnOnlyObservation(adapter, command);
  }

  private void assertSystemFailedWithWarnOnlyObservation(
      ApimForgotPasswordUsernameLookupAdapter adapter,
      StartForgotPasswordUsernameLookupCommand command) {
    Logger logger =
        (Logger) LoggerFactory.getLogger(ApimForgotPasswordUsernameLookupAdapter.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      StepVerifier.create(adapter.lookup(command))
          .assertNext(
              result -> {
                assertThat(result.status())
                    .isEqualTo(ForgotPasswordUsernameLookupStatus.SYSTEM_FAILED);
                assertThat(result.failureOrigin()).isEqualTo(FailureOrigin.DOWNSTREAM_CONTRACT);
              })
          .verifyComplete();

      assertThat(appender.list)
          .singleElement()
          .satisfies(
              event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getFormattedMessage())
                    .contains("classification=DOWNSTREAM_CONTRACT")
                    .doesNotContain(
                        "response envelope is missing response",
                        "invalid/malformed response",
                        RAW_MARKER);
              });
      assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.ERROR);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }
}

package com.bct.ngtpa.apiservice.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureEvent;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.port.out.FailureEventPort;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

class StructuredFailureEventLoggerTest {

  private final LoggingSanitizer sanitizer = mock(LoggingSanitizer.class);
  private final StructuredFailureEventLogger events = new StructuredFailureEventLogger(sanitizer);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private Logger logger;

  @BeforeEach
  void attachAppender() {
    when(sanitizer.toSafeString(any())).thenReturn("{\"safe\":true}");
    logger = (Logger) LoggerFactory.getLogger(StructuredFailureEventLogger.class);
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void ignoresNullFailureForBothPortMethods() {
    events.publish(null);
    events.publish(null, new IllegalStateException("unused"));

    assertThat(appender.list).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("originsAndLevels")
  void selectsLevelByFailureOrigin(FailureOrigin origin, Level expectedLevel) {
    events.publish(failure(origin));

    ILoggingEvent logged = singleEvent();
    assertThat(logged.getLevel()).isEqualTo(expectedLevel);
    assertThat(logged.getThrowableProxy()).isNull();
    verify(sanitizer).toSafeString(any());
  }

  @ParameterizedTest
  @MethodSource("originsAndLevels")
  void causeOverloadAddsSanitizedMetadataAndAttachesTraceOnlyAtError(
      FailureOrigin origin, Level expectedLevel) {
    var root = new IllegalArgumentException("token=root-secret");
    var cause = new IllegalStateException("policyNo=P001 outer", root);

    events.publish(failure(origin), cause);

    ILoggingEvent logged = singleEvent();
    assertThat(logged.getLevel()).isEqualTo(expectedLevel);
    if (expectedLevel == Level.ERROR) {
      assertThat(logged.getThrowableProxy()).isNotNull();
      assertThat(logged.getThrowableProxy().getClassName())
          .isEqualTo(IllegalStateException.class.getName());
    } else {
      assertThat(logged.getThrowableProxy()).isNull();
    }
    verify(sanitizer)
        .toSafeString(
            org.mockito.ArgumentMatchers.argThat(
                value -> {
                  if (!(value instanceof Map<?, ?> event)) return false;
                  return "IllegalStateException".equals(event.get("exceptionType"))
                      && "IllegalArgumentException".equals(event.get("rootCauseType"))
                      && "policyNo=P001 outer".equals(event.get("sanitizedMessage"));
                }));
  }

  @Test
  void defaultCauseOverloadPreservesCompatibilityForOtherPortImplementations() {
    var recorded = new java.util.concurrent.atomic.AtomicReference<FailureEvent>();
    FailureEventPort port = recorded::set;
    FailureEvent failure = failure(FailureOrigin.LOCAL_SYSTEM);

    port.publish(failure, new IllegalStateException("ignored by legacy implementation"));

    assertThat(recorded.get()).isSameAs(failure);
  }

  @Test
  void causeOverloadOmitsCauseMetadataWhenCauseIsNull() {
    events.publish(failure(FailureOrigin.LOCAL_SYSTEM), null);

    singleEvent();
    verify(sanitizer)
        .toSafeString(
            org.mockito.ArgumentMatchers.argThat(
                value ->
                    value instanceof Map<?, ?> event
                        && !event.containsKey("exceptionType")
                        && !event.containsKey("rootCauseType")
                        && !event.containsKey("sanitizedMessage")));
  }

  @Test
  void cyclicCauseChainTerminatesAtTheLastDistinctCause() {
    var first = new CyclicFailure("first");
    var second = new CyclicFailure("second");
    first.next = second;
    second.next = first;

    events.publish(failure(FailureOrigin.LOCAL_SYSTEM), first);

    singleEvent();
    verify(sanitizer)
        .toSafeString(
            org.mockito.ArgumentMatchers.argThat(
                value ->
                    value instanceof Map<?, ?> event
                        && "CyclicFailure".equals(event.get("rootCauseType"))));
  }

  private static Stream<Arguments> originsAndLevels() {
    return Stream.of(
        Arguments.of(FailureOrigin.LOCAL_VALIDATION, Level.WARN),
        Arguments.of(FailureOrigin.LOCAL_BUSINESS, Level.WARN),
        Arguments.of(FailureOrigin.DOWNSTREAM_BUSINESS, Level.WARN),
        Arguments.of(FailureOrigin.DOWNSTREAM_CONTRACT, Level.ERROR),
        Arguments.of(FailureOrigin.DOWNSTREAM_INVALID_RESPONSE, Level.ERROR),
        Arguments.of(FailureOrigin.DOWNSTREAM_TRANSPORT, Level.ERROR),
        Arguments.of(FailureOrigin.LOCAL_SYSTEM, Level.ERROR));
  }

  private static FailureEvent failure(FailureOrigin origin) {
    return new FailureEvent(
        "test.failure",
        "req-1",
        "TEST",
        "operation",
        origin,
        "internal.code",
        "public.code",
        503,
        true,
        Map.of("safe", "dimension"));
  }

  private ILoggingEvent singleEvent() {
    assertThat(appender.list).hasSize(1);
    return appender.list.getFirst();
  }

  private static final class CyclicFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private RuntimeException next;

    CyclicFailure(String message) {
      super(message);
    }

    @Override
    public synchronized Throwable getCause() {
      return next;
    }
  }
}

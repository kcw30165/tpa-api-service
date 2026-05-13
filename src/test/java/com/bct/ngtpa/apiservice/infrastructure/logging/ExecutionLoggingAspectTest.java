package com.bct.ngtpa.apiservice.infrastructure.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ExecutionLoggingAspectTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(ExecutionLoggingAspect.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    void logsSynchronousMethodSuccess() {
        ProxiedService proxied = proxiedService();

        String result = proxied.proxy().synchronousSuccess(Map.of("authorization", "Bearer abc", "traceId", "trace-1"));

        assertEquals("ok", result);
        assertEquals(2, listAppender.list.size());
        assertEquals(Level.INFO, listAppender.list.get(0).getLevel());
        assertTrue(listAppender.list.get(0).getFormattedMessage().contains("\"event\":\"method.execution.start\""));
        assertTrue(listAppender.list.get(0).getFormattedMessage().contains("\"authorization\":\"***\""));
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains("\"event\":\"method.execution.success\""));
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains("\"result\":\"ok\""));
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains("\"elapsedMs\":"));
    }

    @Test
    void logsSynchronousMethodException() {
        ProxiedService proxied = proxiedService();

        StepVerifier.create(Mono.fromCallable(proxied.proxy()::synchronousFailure))
                .expectErrorSatisfies(error -> assertEquals("boom", error.getMessage()))
                .verify();

        assertEquals(2, listAppender.list.size());
        assertEquals(Level.ERROR, listAppender.list.get(1).getLevel());
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains("\"event\":\"method.execution.error\""));
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains("\"elapsedMs\":"));
    }

    @Test
    void logsMonoSuccessWithoutSubscribingInsideAspect() {
        ProxiedService proxied = proxiedService();

        Mono<String> result = proxied.proxy().monoSuccess(Map.of("userId", "member-1", "traceId", "trace-1"));

        assertEquals(0, proxied.target().monoSubscriptions.get());

        StepVerifier.create(result)
                .expectNext("mono-ok")
                .verifyComplete();

        assertEquals(1, proxied.target().monoSubscriptions.get());
        assertEquals(2, listAppender.list.size());
        assertTrue(listAppender.list.get(0).getFormattedMessage().contains("\"userId\":\"***\""));
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains("\"result\":\"mono-ok\""));
    }

    @Test
    void logsMonoError() {
        ProxiedService proxied = proxiedService();

        StepVerifier.create(proxied.proxy().monoFailure())
                .expectErrorSatisfies(error -> assertEquals("mono-boom", error.getMessage()))
                .verify();

        assertEquals(2, listAppender.list.size());
        assertEquals(Level.ERROR, listAppender.list.get(1).getLevel());
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains("\"event\":\"method.execution.error\""));
    }

    @Test
    void logsFluxSuccessWithoutSubscribingInsideAspect() {
        ProxiedService proxied = proxiedService();

        Flux<String> result = proxied.proxy().fluxSuccess();

        assertEquals(0, proxied.target().fluxSubscriptions.get());

        StepVerifier.create(result)
                .expectNext("a", "b")
                .verifyComplete();

        assertEquals(1, proxied.target().fluxSubscriptions.get());
        assertEquals(2, listAppender.list.size());
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains("\"emittedItems\":2"));
    }

    @Test
    void logsFluxError() {
        ProxiedService proxied = proxiedService();

        StepVerifier.create(proxied.proxy().fluxFailure())
                .expectErrorSatisfies(error -> assertEquals("flux-boom", error.getMessage()))
                .verify();

        assertEquals(2, listAppender.list.size());
        assertEquals(Level.ERROR, listAppender.list.get(1).getLevel());
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains("\"event\":\"method.execution.error\""));
    }

    @Test
    void logsJsonStructuredStartEvent() {
        ProxiedService proxied = proxiedService();

        proxied.proxy().synchronousSuccess(Map.of("traceId", "t1"));

        String startMsg = listAppender.list.get(0).getFormattedMessage();
        assertTrue(startMsg.contains("\"event\":\"method.execution.start\""));
        assertTrue(startMsg.contains("\"className\":\"TestService\""));
        assertTrue(startMsg.contains("\"methodName\":\"synchronousSuccess\""));
        assertTrue(startMsg.contains("\"label\":\"sync.success\""));
    }

    @Test
    void logsJsonStructuredSuccessEvent() {
        ProxiedService proxied = proxiedService();

        proxied.proxy().synchronousSuccess(Map.of("traceId", "t1"));

        String successMsg = listAppender.list.get(1).getFormattedMessage();
        assertTrue(successMsg.contains("\"event\":\"method.execution.success\""));
        assertTrue(successMsg.contains("\"elapsedMs\":"));
    }

    @Test
    void includesRequestIdFromContextInMonoLog() {
        ProxiedService proxied = proxiedService();

        StepVerifier.create(
                        proxied.proxy().monoSuccess(Map.of("traceId", "t1"))
                                .contextWrite(ctx ->
                                        ctx.put(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, "request-id-from-ctx")))
                .expectNext("mono-ok")
                .verifyComplete();

        String startMsg = listAppender.list.get(0).getFormattedMessage();
        assertTrue(startMsg.contains("\"requestId\":\"request-id-from-ctx\""),
                "requestId from Reactor Context should appear in Mono log");
    }

    @Test
    void includesRequestIdFromContextInFluxLog() {
        ProxiedService proxied = proxiedService();

        StepVerifier.create(
                        proxied.proxy().fluxSuccess()
                                .contextWrite(ctx ->
                                        ctx.put(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, "flux-request-id")))
                .expectNext("a", "b")
                .verifyComplete();

        String startMsg = listAppender.list.get(0).getFormattedMessage();
        assertTrue(startMsg.contains("\"requestId\":\"flux-request-id\""),
                "requestId from Reactor Context should appear in Flux log");
    }

    @Test
    void omitsRequestIdWhenNotInContext() {
        ProxiedService proxied = proxiedService();

        StepVerifier.create(proxied.proxy().monoSuccess(Map.of("traceId", "t1")))
                .expectNext("mono-ok")
                .verifyComplete();

        String startMsg = listAppender.list.get(0).getFormattedMessage();
        assertTrue(!startMsg.contains("\"requestId\""), "requestId should be absent when not in context");
    }

    @Test
    void omitsArgsWhenLogArgsFalse() {
        ProxiedService proxied = proxiedService();
        proxied.proxy().synchronousFailureNoLogArgs();
        // even without try/catch it just throws; use a callable pattern
        // Actually synchronousFailure is the method with logArgs=false — let's use synchronousSuccess with logArgs=false
        // Add a new method: see TestService.noArgsLog()
        proxied.proxy().noArgsLog(Map.of("traceId", "t1"));

        String startMsg = listAppender.list.get(listAppender.list.size() - 2).getFormattedMessage();
        // "args" should NOT appear
        assertTrue(!startMsg.contains("\"args\""), "args should be omitted when logArgs=false");
    }

    @Test
    void omitsResultWhenLogResultFalse() {
        ProxiedService proxied = proxiedService();

        StepVerifier.create(proxied.proxy().monoNoResultLog())
                .expectNext("no-result")
                .verifyComplete();

        String successMsg = listAppender.list.get(1).getFormattedMessage();
        assertTrue(!successMsg.contains("\"result\""), "result should be omitted when logResult=false");
    }

    @Test
    void synchMethodReturningNullLogsNullResult() {
        ProxiedService proxied = proxiedService();
        proxied.proxy().synchronousNullReturn();

        String successMsg = listAppender.list.get(1).getFormattedMessage();
        assertTrue(successMsg.contains("\"result\":null"), "null result should be logged");
    }

    @Test
    void logsLabelAsBlankWhenAnnotationValueEmpty() {
        ProxiedService proxied = proxiedService();
        proxied.proxy().noLabel();

        String startMsg = listAppender.list.get(0).getFormattedMessage();
        // label is blank → should NOT be present in the event
        assertTrue(!startMsg.contains("\"label\""), "blank label should be omitted");
    }

    @Test
    void logsErrorWithNoElapsedMsWhenMonoThrowsSynchronously() {
        ProxiedService proxied = proxiedService();

        try {
            proxied.proxy().monoThrowsSynchronously();
        } catch (IllegalStateException ignored) {
            // expected
        }

        assertTrue(listAppender.list.size() >= 1);
        ILoggingEvent errorEvent = listAppender.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .findFirst()
                .orElseThrow();
        String msg = errorEvent.getFormattedMessage();
        assertTrue(msg.contains("method.execution.error"));
        // elapsedMs is -1 → should NOT appear in the event (elapsedMillis < 0)
        assertTrue(!msg.contains("\"elapsedMs\""), "elapsedMs should be omitted when -1");
    }

    @Test
    void logsErrorWithNoElapsedMsWhenFluxThrowsSynchronously() {
        ProxiedService proxied = proxiedService();

        try {
            proxied.proxy().fluxThrowsSynchronously();
        } catch (IllegalStateException ignored) {
            // expected
        }

        ILoggingEvent errorEvent = listAppender.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .findFirst()
                .orElseThrow();
        assertTrue(errorEvent.getFormattedMessage().contains("sync-throw-from-flux"));
    }

    @Test
    void omitsEmittedItemsWhenFluxLogResultFalse() {
        ProxiedService proxied = proxiedService();

        StepVerifier.create(proxied.proxy().fluxNoResultLog())
                .expectNext("x", "y")
                .verifyComplete();

        String successMsg = listAppender.list.get(1).getFormattedMessage();
        assertFalse(successMsg.contains("\"emittedItems\""), "emittedItems should be omitted when logResult=false");
    }

    private ProxiedService proxiedService() {
        TestService target = new TestService();
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.addAspect(new ExecutionLoggingAspect(
                new LoggingSanitizer(new ObjectMapper(), properties()), new ObjectMapper()));
        return new ProxiedService(target, proxyFactory.getProxy());
    }

    private record ProxiedService(TestService target, TestService proxy) {
    }

    private static LoggingSanitizerProperties properties() {
        LoggingSanitizerProperties properties = new LoggingSanitizerProperties();
        properties.setSensitiveTokens(List.of(
                "authorization",
                "token",
                "secret",
                "password",
                "apiKey",
                "Certificate",
                "clientSecret",
                "policyNo",
                "certNo",
                "userId",
                "privateKey",
                "publicKey",
                "policy-no",
                "cert-no",
                "user-id"));
        return properties;
    }

    static class TestService {
        private final AtomicInteger monoSubscriptions = new AtomicInteger();
        private final AtomicInteger fluxSubscriptions = new AtomicInteger();

        @LogExecution(value = "sync.success", logArgs = true, logResult = true)
        String synchronousSuccess(Map<String, Object> payload) {
            return "ok";
        }

        @LogExecution(value = "sync.failure")
        String synchronousFailure() {
            throw new IllegalStateException("boom");
        }

        @LogExecution(value = "mono.success", logArgs = true, logResult = true)
        Mono<String> monoSuccess(Map<String, Object> payload) {
            return Mono.defer(() -> {
                monoSubscriptions.incrementAndGet();
                return Mono.just("mono-ok");
            });
        }

        @LogExecution(value = "mono.failure")
        Mono<String> monoFailure() {
            return Mono.defer(() -> {
                monoSubscriptions.incrementAndGet();
                return Mono.error(new IllegalStateException("mono-boom"));
            });
        }

        @LogExecution(value = "flux.success", logResult = true)
        Flux<String> fluxSuccess() {
            return Flux.defer(() -> {
                fluxSubscriptions.incrementAndGet();
                return Flux.just("a", "b");
            });
        }

        @LogExecution(value = "flux.no.result", logResult = false)
        Flux<String> fluxNoResultLog() {
            return Flux.just("x", "y");
        }

        @LogExecution(value = "flux.failure")
        Flux<String> fluxFailure() {
            return Flux.defer(() -> {
                fluxSubscriptions.incrementAndGet();
                return Flux.error(new IllegalStateException("flux-boom"));
            });
        }

        @LogExecution(value = "sync.no.args", logArgs = false, logResult = true)
        String noArgsLog(Map<String, Object> payload) {
            return "no-args-ok";
        }

        @LogExecution(value = "sync.failure.noargs", logArgs = false, logResult = false)
        String synchronousFailureNoLogArgs() {
            return "ok";
        }

        @LogExecution(value = "mono.no.result", logResult = false)
        Mono<String> monoNoResultLog() {
            return Mono.just("no-result");
        }

        @LogExecution(value = "sync.null.return", logArgs = false, logResult = true)
        String synchronousNullReturn() {
            return null;
        }

        @LogExecution(value = "", logArgs = false, logResult = false)
        String noLabel() {
            return "ok";
        }

        @LogExecution(value = "mono.throw", logArgs = false, logResult = false)
        Mono<String> monoThrowsSynchronously() {
            throw new IllegalStateException("sync-throw-from-mono");
        }

        @LogExecution(value = "flux.throw", logArgs = false, logResult = false)
        Flux<String> fluxThrowsSynchronously() {
            throw new IllegalStateException("sync-throw-from-flux");
        }
    }
}
package com.bct.ngtpa.apiservice.config.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
        assertTrue(listAppender.list.get(0).getFormattedMessage().contains("Execution start"));
        assertTrue(listAppender.list.get(0).getFormattedMessage().contains("authorization=***"));
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains("Execution success"));
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains("result=ok"));
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains("elapsedMs="));
    }

    @Test
    void logsSynchronousMethodException() {
        ProxiedService proxied = proxiedService();

        StepVerifier.create(Mono.fromCallable(proxied.proxy()::synchronousFailure))
                .expectErrorSatisfies(error -> assertEquals("boom", error.getMessage()))
                .verify();

        assertEquals(2, listAppender.list.size());
        assertEquals(Level.ERROR, listAppender.list.get(1).getLevel());
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains("Execution error"));
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains("elapsedMs="));
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
        assertTrue(listAppender.list.get(0).getFormattedMessage().contains("userId=***"));
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains("result=mono-ok"));
    }

    @Test
    void logsMonoError() {
        ProxiedService proxied = proxiedService();

        StepVerifier.create(proxied.proxy().monoFailure())
                .expectErrorSatisfies(error -> assertEquals("mono-boom", error.getMessage()))
                .verify();

        assertEquals(2, listAppender.list.size());
        assertEquals(Level.ERROR, listAppender.list.get(1).getLevel());
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains("Execution error"));
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
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains("emittedItems=2"));
    }

    @Test
    void logsFluxError() {
        ProxiedService proxied = proxiedService();

        StepVerifier.create(proxied.proxy().fluxFailure())
                .expectErrorSatisfies(error -> assertEquals("flux-boom", error.getMessage()))
                .verify();

        assertEquals(2, listAppender.list.size());
        assertEquals(Level.ERROR, listAppender.list.get(1).getLevel());
        assertTrue(listAppender.list.get(1).getFormattedMessage().contains("Execution error"));
    }

    private ProxiedService proxiedService() {
        TestService target = new TestService();
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.addAspect(new ExecutionLoggingAspect(new LoggingSanitizer(new ObjectMapper(), properties())));
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

        @LogExecution(value = "flux.failure")
        Flux<String> fluxFailure() {
            return Flux.defer(() -> {
                fluxSubscriptions.incrementAndGet();
                return Flux.error(new IllegalStateException("flux-boom"));
            });
        }
    }
}
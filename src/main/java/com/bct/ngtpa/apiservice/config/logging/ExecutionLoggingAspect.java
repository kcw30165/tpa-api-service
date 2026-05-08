package com.bct.ngtpa.apiservice.config.logging;

import com.bct.ngtpa.apiservice.shared.logging.LogExecution;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Aspect
@Component
public class ExecutionLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(ExecutionLoggingAspect.class);

    private final LoggingSanitizer loggingSanitizer;
    private final ObjectMapper objectMapper;

    public ExecutionLoggingAspect(LoggingSanitizer loggingSanitizer, ObjectMapper objectMapper) {
        this.loggingSanitizer = loggingSanitizer;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(logExecution)")
    public Object logExecution(ProceedingJoinPoint joinPoint, LogExecution logExecution) throws Throwable {
        InvocationContext context = InvocationContext.from(joinPoint, logExecution);
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> returnType = signature.getReturnType();

        if (!Mono.class.isAssignableFrom(returnType) && !Flux.class.isAssignableFrom(returnType)) {
            String requestId = MDC.get(RequestLoggingWebFilter.REQUEST_ID_CONTEXT_KEY);
            long startNanos = System.nanoTime();
            logStart(context, requestId);
            try {
                Object result = joinPoint.proceed();
                logSuccess(context, elapsedMillis(startNanos), result, requestId);
                return result;
            } catch (Throwable throwable) {
                logError(context, elapsedMillis(startNanos), throwable, requestId);
                throw throwable;
            }
        }

        try {
            Object result = joinPoint.proceed();

            if (result instanceof Mono<?> mono) {
                return decorateMono(mono, context);
            }
            if (result instanceof Flux<?> flux) {
                return decorateFlux(flux, context);
            }
            return result;
        } catch (Throwable throwable) {
            logError(context, -1L, throwable, null);
            throw throwable;
        }
    }

    private Mono<?> decorateMono(Mono<?> mono, InvocationContext context) {
        return Mono.deferContextual(ctx -> {
            String requestId = ctx.getOrDefault(RequestLoggingWebFilter.REQUEST_ID_CONTEXT_KEY, null);
            AtomicLong startNanos = new AtomicLong();
            return mono
                    .doOnSubscribe(subscription -> {
                        startNanos.set(System.nanoTime());
                        logStart(context, requestId);
                    })
                    .doOnSuccess(result -> logSuccess(context, elapsedMillis(startNanos.get()), result, requestId))
                    .doOnError(throwable -> logError(context, elapsedMillis(startNanos.get()), throwable, requestId));
        });
    }

    private Flux<?> decorateFlux(Flux<?> flux, InvocationContext context) {
        return Flux.deferContextual(ctx -> {
            String requestId = ctx.getOrDefault(RequestLoggingWebFilter.REQUEST_ID_CONTEXT_KEY, null);
            AtomicLong startNanos = new AtomicLong();
            AtomicLong itemCount = new AtomicLong();
            return flux
                    .doOnSubscribe(subscription -> {
                        startNanos.set(System.nanoTime());
                        logStart(context, requestId);
                    })
                    .doOnNext(item -> itemCount.incrementAndGet())
                    .doOnComplete(() -> logFluxSuccess(context, elapsedMillis(startNanos.get()), itemCount.get(), requestId))
                    .doOnError(throwable -> logError(context, elapsedMillis(startNanos.get()), throwable, requestId));
        });
    }

    private void logStart(InvocationContext context, String requestId) {
        Map<String, Object> event = buildEventBase("method.execution.start", context, requestId);
        if (context.logArgs()) {
            event.put("args", loggingSanitizer.sanitizeArguments(context.args()));
        }
        log.info("{}", toJson(event));
    }

    private void logSuccess(InvocationContext context, long elapsedMillis, Object result, String requestId) {
        Map<String, Object> event = buildEventBase("method.execution.success", context, requestId);
        event.put("elapsedMs", elapsedMillis);
        if (context.logResult()) {
            event.put("result", loggingSanitizer.sanitizeValue(result));
        }
        log.info("{}", toJson(event));
    }

    private void logFluxSuccess(InvocationContext context, long elapsedMillis, long itemCount, String requestId) {
        Map<String, Object> event = buildEventBase("method.execution.success", context, requestId);
        event.put("elapsedMs", elapsedMillis);
        if (context.logResult()) {
            event.put("emittedItems", itemCount);
        }
        log.info("{}", toJson(event));
    }

    private void logError(InvocationContext context, long elapsedMillis, Throwable throwable, String requestId) {
        Map<String, Object> event = buildEventBase("method.execution.error", context, requestId);
        if (elapsedMillis >= 0) {
            event.put("elapsedMs", elapsedMillis);
        }
        event.put("exceptionType", throwable.getClass().getSimpleName());
        event.put("errorMessage", loggingSanitizer.toSafeString(throwable.getMessage()));
        log.error("{}", toJson(event), throwable);
    }

    private Map<String, Object> buildEventBase(String eventName, InvocationContext context, String requestId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", eventName);
        if (requestId != null) {
            event.put("requestId", requestId);
        }
        event.put("className", context.className());
        event.put("methodName", context.methodName());
        if (context.label() != null && !context.label().isBlank()) {
            event.put("label", context.label());
        }
        return event;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"event\":\"log-serialization-failed\"}";
        }
    }

    private long elapsedMillis(long startNanos) {
        if (startNanos <= 0) {
            return -1L;
        }
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private record InvocationContext(
            String className,
            String methodName,
            String label,
            boolean logArgs,
            boolean logResult,
            Object[] args) {

        static InvocationContext from(ProceedingJoinPoint joinPoint, LogExecution logExecution) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            return new InvocationContext(
                    signature.getDeclaringType().getSimpleName(),
                    signature.getName(),
                    logExecution.value(),
                    logExecution.logArgs(),
                    logExecution.logResult(),
                    joinPoint.getArgs());
        }
    }
}
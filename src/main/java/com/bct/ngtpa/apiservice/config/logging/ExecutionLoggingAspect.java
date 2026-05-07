package com.bct.ngtpa.apiservice.config.logging;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Aspect
@Component
public class ExecutionLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(ExecutionLoggingAspect.class);

    private final LoggingSanitizer loggingSanitizer;

    public ExecutionLoggingAspect(LoggingSanitizer loggingSanitizer) {
        this.loggingSanitizer = loggingSanitizer;
    }

    @Around("@annotation(logExecution)")
    public Object logExecution(ProceedingJoinPoint joinPoint, LogExecution logExecution) throws Throwable {
        InvocationContext context = InvocationContext.from(joinPoint, logExecution);
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> returnType = signature.getReturnType();

        if (!Mono.class.isAssignableFrom(returnType) && !Flux.class.isAssignableFrom(returnType)) {
            long startNanos = System.nanoTime();
            logStart(context);
            try {
                Object result = joinPoint.proceed();
                logSuccess(context, elapsedMillis(startNanos), result);
                return result;
            } catch (Throwable throwable) {
                logError(context, elapsedMillis(startNanos), throwable);
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
            logError(context, -1L, throwable);
            throw throwable;
        }
    }

    private Mono<?> decorateMono(Mono<?> mono, InvocationContext context) {
        AtomicLong startNanos = new AtomicLong();
        return mono
                .doOnSubscribe(subscription -> {
                    startNanos.set(System.nanoTime());
                    logStart(context);
                })
                .doOnSuccess(result -> logSuccess(context, elapsedMillis(startNanos.get()), result))
                .doOnError(throwable -> logError(context, elapsedMillis(startNanos.get()), throwable));
    }

    private Flux<?> decorateFlux(Flux<?> flux, InvocationContext context) {
        AtomicLong startNanos = new AtomicLong();
        AtomicLong itemCount = new AtomicLong();
        return flux
                .doOnSubscribe(subscription -> {
                    startNanos.set(System.nanoTime());
                    logStart(context);
                })
                .doOnNext(item -> itemCount.incrementAndGet())
                .doOnComplete(() -> logFluxSuccess(context, elapsedMillis(startNanos.get()), itemCount.get()))
                .doOnError(throwable -> logError(context, elapsedMillis(startNanos.get()), throwable));
    }

    private void logStart(InvocationContext context) {
        Map<String, Object> fields = context.baseFields();
        if (context.logArgs()) {
            fields.put("args", loggingSanitizer.sanitizeArguments(context.args()));
        }
        log.info("Execution start {}", fields);
    }

    private void logSuccess(InvocationContext context, long elapsedMillis, Object result) {
        Map<String, Object> fields = context.baseFields();
        fields.put("elapsedMs", elapsedMillis);
        if (context.logResult()) {
            fields.put("result", loggingSanitizer.sanitizeValue(result));
        }
        log.info("Execution success {}", fields);
    }

    private void logFluxSuccess(InvocationContext context, long elapsedMillis, long itemCount) {
        Map<String, Object> fields = context.baseFields();
        fields.put("elapsedMs", elapsedMillis);
        if (context.logResult()) {
            fields.put("emittedItems", itemCount);
        }
        log.info("Execution success {}", fields);
    }

    private void logError(InvocationContext context, long elapsedMillis, Throwable throwable) {
        Map<String, Object> fields = context.baseFields();
        if (elapsedMillis >= 0) {
            fields.put("elapsedMs", elapsedMillis);
        }
        log.error("Execution error {}", fields, throwable);
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

        Map<String, Object> baseFields() {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("className", className);
            fields.put("methodName", methodName);
            if (label != null && !label.isBlank()) {
                fields.put("label", label);
            }
            return fields;
        }
    }
}
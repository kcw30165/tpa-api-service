package com.bct.ngtpa.apiservice.adapter.in.web.sort;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * AOP aspect that intercepts methods annotated with {@link ApplySorts} and applies
 * the configured sorting logic via {@link SortEngine}.
 *
 * <p>Supported return types:
 * <ul>
 *   <li><strong>Synchronous</strong> – sorting is applied inline before the value is returned.</li>
 *   <li>{@link Mono}{@code <T>} – sorting is applied inside
 *       {@link Mono#map(java.util.function.Function) .map()} so the aspect never subscribes
 *       or blocks internally.</li>
 * </ul>
 *
 * <p>TODO: {@link reactor.core.publisher.Flux}{@code <T>} sorting is not yet implemented.
 * Existing endpoints use {@link Mono}, so Flux support can be added later without changing the
 * {@link ApplySorts} annotation contract.
 *
 * <p>This aspect lives in the web adapter ({@code adapter.in.web.sort}) and must not be applied
 * to application use case methods.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class ApplySortsAspect {

    private final SortEngine sortEngine;

    /**
     * Intercepts any method annotated with {@link ApplySorts} and sorts the return value
     * according to the declared {@link SortList} configurations.
     *
     * <p>For {@link Mono} returns the sort is deferred into a {@code .map()} operator;
     * for all other types it is applied synchronously.
     */
    @Around("@annotation(applySorts)")
    public Object applySorts(ProceedingJoinPoint pjp, ApplySorts applySorts) throws Throwable {
        Object result = pjp.proceed();

        if (result instanceof Mono<?> mono) {
            // Defer sorting into the reactive chain – no subscription inside the aspect
            return mono.map(value -> sortEngine.sort(value, applySorts));
        }

        // TODO: Flux<T> sorting not yet implemented.
        //       Add when the first Flux-returning endpoint requires sorted output.
        //       The annotation contract does not need to change.
        return sortEngine.sort(result, applySorts);
    }
}

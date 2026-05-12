package com.bct.ngtpa.apiservice.adapter.in.web.sort;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opt-in annotation that instructs {@link ApplySortsAspect} to sort specific lists in the
 * method's return value before it is returned to the caller.
 *
 * <p>Usage example (JSON endpoint):
 * <pre>{@code
 * @ApplySorts({
 *     @SortList(
 *         path = "items",
 *         by = @SortBy(field = "tradeDate", direction = SortDirection.DESC,
 *                      type = SortType.DATE, datePattern = "dd/MM/yyyy")
 *     )
 * })
 * public TradeListResult sort(TradeListResult result) { return result; }
 * }</pre>
 *
 * <p>Supported return types:
 * <ul>
 *   <li>Synchronous values – sorted inline.</li>
 *   <li>{@link reactor.core.publisher.Mono}{@code <T>} – sorting is applied via
 *       {@link reactor.core.publisher.Mono#map(java.util.function.Function) .map()} without
 *       subscribing inside the aspect.</li>
 * </ul>
 *
 * <p>WARNING: This annotation is opt-in and explicit. It does NOT recursively sort all lists
 * in the return value. Only the lists identified by {@link SortList#path()} are sorted.
 *
 * <p>NOTE: Do not apply this annotation to application use case methods.
 * It belongs exclusively to the web adapter / presentation layer.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ApplySorts {

    /** One or more list sort configurations to apply in declaration order. */
    SortList[] value();
}

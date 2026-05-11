package com.bct.ngtpa.apiservice.adapter.in.web.sort;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares which list to sort and how.
 *
 * <p>{@code path} identifies the target list relative to the annotated method's return value
 * using a dot-separated accessor path, e.g. {@code "report.rows"} or {@code "items"}.
 *
 * <p>Currently supported path styles:
 * <ul>
 *   <li>Direct field: {@code "items"}, {@code "rows"}</li>
 *   <li>Nested object path: {@code "report.rows"}, {@code "report.sources"}, {@code "data.items"}</li>
 * </ul>
 *
 * <p>TODO: Collection traversal paths (e.g. {@code "items[].breakdown.rows"}) are not yet
 * implemented. They can be added without changing the annotation contract.
 *
 * <p>Only the list identified by {@code path} is sorted. No other lists are touched.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface SortList {

    /**
     * Dot-separated accessor path to the list that should be sorted,
     * relative to the method's return value.
     */
    String path();

    /** One or more sort rules applied in declaration order. */
    SortBy[] by();
}

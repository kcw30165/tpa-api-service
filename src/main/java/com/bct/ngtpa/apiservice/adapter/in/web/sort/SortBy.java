package com.bct.ngtpa.apiservice.adapter.in.web.sort;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a single field sort rule within a {@link SortList}.
 *
 * <p>{@code field} may be a simple accessor name (e.g. {@code "dealingDate"}) or a
 * dot-separated nested path (e.g. {@code "period.fromDate"}). Accessors are resolved via the
 * Java record accessor method for each path segment.
 *
 * <p>Multiple {@link SortBy} rules on the same {@link SortList} form a compound comparator applied
 * in declaration order (primary, secondary, tertiary, …).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface SortBy {

    /** Accessor name or dot-separated path to the field to sort by. */
    String field();

    /** Sort direction. Defaults to {@link SortDirection#ASC}. */
    SortDirection direction() default SortDirection.ASC;

    /** Value type used to determine comparison semantics. Defaults to {@link SortType#STRING}. */
    SortType type() default SortType.STRING;

    /**
     * Date format pattern for {@link SortType#DATE} sorting.
     * When empty, ISO local date format is assumed.
     */
    String datePattern() default "";

    /**
     * When {@code true} (default), {@code null}/blank/unparseable values sort after non-null values.
     * Set to {@code false} to place them first.
     */
    boolean nullsLast() default true;
}

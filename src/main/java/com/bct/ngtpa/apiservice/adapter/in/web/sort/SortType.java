package com.bct.ngtpa.apiservice.adapter.in.web.sort;

/**
 * Type of value to sort by, used with {@link SortBy}.
 *
 * <ul>
 *   <li>{@link #STRING} – lexicographic string comparison.</li>
 *   <li>{@link #NUMBER} – numeric comparison via {@link java.math.BigDecimal}.</li>
 *   <li>{@link #DATE}   – parsed date comparison via {@link java.time.LocalDate} using the
 *       {@link SortBy#datePattern()}. Blank or unparseable values are treated as {@code null}
 *       and placed according to {@link SortBy#nullsLast()}.</li>
 *   <li>{@link #BOOLEAN} – natural boolean ordering (false &lt; true).</li>
 * </ul>
 */
public enum SortType {
    STRING,
    NUMBER,
    DATE,
    BOOLEAN
}

package com.bct.ngtpa.apiservice.adapter.out.config;

import lombok.Getter;
import lombok.Setter;

import java.math.RoundingMode;

/**
 * Configuration spec for formatting a numeric amount.
 */
@Getter
@Setter
public class AmountFormatConfig {

    /** Minimum fraction digits to display. */
    private int minFractionDigits = 0;

    /** Maximum fraction digits to display. */
    private int maxFractionDigits = 2;

    /** Grouping separator character, e.g. "," for US style. */
    private String groupingSeparator = ",";

    /** Decimal separator character, e.g. "." for US style. */
    private String decimalSeparator = ".";

    /** Rounding mode applied before display. */
    private RoundingMode roundingMode = RoundingMode.HALF_UP;

    /** Whether to strip trailing decimal zeros after rounding. */
    private boolean stripTrailingZeros = true;

    /**
     * Negative number presentation style.
     * Supported: {@code "minus"} (default, prepend '-'), {@code "parentheses"} (wrap in parentheses).
     */
    private String negativeStyle = "minus";
}

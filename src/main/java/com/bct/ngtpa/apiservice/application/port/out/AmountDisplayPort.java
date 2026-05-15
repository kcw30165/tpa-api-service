package com.bct.ngtpa.apiservice.application.port.out;

import java.math.BigDecimal;

public interface AmountDisplayPort {

    /**
     * Formats a numeric amount as a display string for the given locale/context.
     *
     * @param amount      the numeric amount (may be null; treat as zero)
     * @param lang        locale/language code, e.g. "en" or "zh_HK"
     * @param accountEnv  account environment, e.g. "JP"
     * @param trustCode   trust code from access token (empty when not yet available)
     * @param schemeType  scheme type from access token (empty when not yet available)
     * @return formatted amount string
     */
    String formatAmount(BigDecimal amount, String lang, String accountEnv, String trustCode, String schemeType);
}

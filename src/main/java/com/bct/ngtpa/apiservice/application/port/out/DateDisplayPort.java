package com.bct.ngtpa.apiservice.application.port.out;

import java.time.LocalDate;

public interface DateDisplayPort {

    /**
     * Formats a {@link LocalDate} as a display string for the given locale/context.
     *
     * @param date        the date to format
     * @param lang        locale/language code, e.g. "en" or "zh_HK"
     * @param env         deployment environment, e.g. "JP"
     * @param trustCode   trust code from access token (empty when not yet available)
     * @param schemeType  scheme type from access token (empty when not yet available)
     * @return formatted date string
     */
    String formatDate(LocalDate date, String lang, String env, String trustCode, String schemeType);
}

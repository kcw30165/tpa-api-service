package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.application.port.out.AmountDisplayPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Config-backed implementation of {@link AmountDisplayPort}.
 *
 * <p>Resolves the format spec using the following fallback order for a given locale:
 * <ol>
 *   <li>{@code ${env}.${trustCode}.${schemeType}}</li>
 *   <li>{@code ${env}.${trustCode}}</li>
 *   <li>{@code ${env}}</li>
 *   <li>{@code default}</li>
 * </ol>
 *
 * <p>Malformed keys caused by blank trustCode/schemeType are skipped.
 * Falls back to a plain {@link BigDecimal#toPlainString()} if no config entry is found.
 */
@Component
@RequiredArgsConstructor
public class ConfigBackedAmountDisplayAdapter implements AmountDisplayPort {

    private static final String DEFAULT_KEY = "default";
    private static final AmountFormatConfig FALLBACK_CONFIG = new AmountFormatConfig();

    private final AmountFormatProperties amountFormatProperties;

    @Override
    public String formatAmount(BigDecimal amount, String lang, String env, String trustCode, String schemeType) {
        var value = amount == null ? BigDecimal.ZERO : amount;
        var config = resolveConfig(lang, env, trustCode, schemeType);
        return applyFormat(value, config);
    }

    private AmountFormatConfig resolveConfig(String lang, String env, String trustCode, String schemeType) {
        var normalizedLang = StringUtils.hasText(lang) ? lang.trim() : "en";
        Map<String, AmountFormatConfig> localeFormats = amountFormatProperties.getLocaleFormats(normalizedLang);

        if (localeFormats.isEmpty()) {
            localeFormats = amountFormatProperties.getLocaleFormats("en");
        }

        for (var candidate : candidateKeys(env, trustCode, schemeType)) {
            var config = localeFormats.get(candidate);
            if (config != null) {
                return config;
            }
        }

        return FALLBACK_CONFIG;
    }

    private String applyFormat(BigDecimal value, AmountFormatConfig config) {
        boolean negative = value.signum() < 0;
        BigDecimal absValue = value.abs();

        // Apply rounding and scale
        RoundingMode roundingMode = config.getRoundingMode() != null ? config.getRoundingMode() : RoundingMode.HALF_UP;
        int maxFrac = config.getMaxFractionDigits();
        BigDecimal scaled = absValue.setScale(maxFrac, roundingMode);

        if (config.isStripTrailingZeros()) {
            scaled = scaled.stripTrailingZeros();
            // Ensure minimum fraction digits after stripping
            if (scaled.scale() < config.getMinFractionDigits()) {
                scaled = scaled.setScale(config.getMinFractionDigits(), roundingMode);
            }
        } else {
            if (scaled.scale() < config.getMinFractionDigits()) {
                scaled = scaled.setScale(config.getMinFractionDigits(), roundingMode);
            }
        }

        // Split into integer and fractional parts
        String plain = scaled.toPlainString();
        int dotIdx = plain.indexOf('.');
        String intPart;
        String fracPart;
        if (dotIdx >= 0) {
            intPart = plain.substring(0, dotIdx);
            fracPart = plain.substring(dotIdx + 1);
        } else {
            intPart = plain;
            fracPart = "";
        }

        // Apply thousands grouping to integer part
        String groupSep = config.getGroupingSeparator();
        String groupedInt = applyGrouping(intPart, groupSep);

        // Build formatted number
        String decSep = config.getDecimalSeparator();
        StringBuilder sb = new StringBuilder(groupedInt);
        if (!fracPart.isEmpty()) {
            sb.append(decSep).append(fracPart);
        }
        String formatted = sb.toString();

        // Apply negative style
        if (negative) {
            String negStyle = config.getNegativeStyle();
            if ("parentheses".equalsIgnoreCase(negStyle)) {
                formatted = "(" + formatted + ")";
            } else {
                formatted = "-" + formatted;
            }
        }

        return formatted;
    }

    private String applyGrouping(String intPart, String groupSep) {
        if (groupSep == null || groupSep.isEmpty() || intPart.length() <= 3) {
            return intPart;
        }
        var sb = new StringBuilder();
        int len = intPart.length();
        int firstGroup = len % 3;
        if (firstGroup > 0) {
            sb.append(intPart, 0, firstGroup);
        }
        for (int i = firstGroup; i < len; i += 3) {
            if (sb.length() > 0) {
                sb.append(groupSep);
            }
            sb.append(intPart, i, i + 3);
        }
        return sb.toString();
    }

    private List<String> candidateKeys(String env, String trustCode, String schemeType) {
        List<String> suffixSegments = new ArrayList<>();
        addIfPresent(suffixSegments, env);
        addIfPresent(suffixSegments, trustCode);
        addIfPresent(suffixSegments, schemeType);

        List<String> candidates = new ArrayList<>();
        for (int size = suffixSegments.size(); size > 0; size--) {
            candidates.add(String.join(".", suffixSegments.subList(0, size)));
        }
        candidates.add(DEFAULT_KEY);
        return List.copyOf(candidates);
    }

    private void addIfPresent(List<String> segments, String value) {
        if (StringUtils.hasText(value)) {
            segments.add(value.trim());
        }
    }
}

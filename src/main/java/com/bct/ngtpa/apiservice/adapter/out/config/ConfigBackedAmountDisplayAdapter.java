package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.application.port.out.AmountDisplayPort;
import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import com.bct.ngtpa.apiservice.shared.config.ConfigLookupContext;
import com.bct.ngtpa.apiservice.shared.config.ConfigLookupRequest;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Config-backed implementation of {@link AmountDisplayPort}.
 *
 * <p>Resolves the decimal format pattern using the following fallback order:
 * <ol>
 *   <li>{@code display-format.amount.<lang>.<trustCode>}</li>
 *   <li>{@code display-format.amount.<lang>.*}</li>
 *   <li>{@code display-format.amount.en.<trustCode>}</li>
 *   <li>{@code display-format.amount.en.*}</li>
 *   <li>Hardcoded fallback: {@value FALLBACK_PATTERN}</li>
 * </ol>
 *
 * <p>The {@code accountEnv} and {@code schemeType} parameters are accepted but not used for pattern
 * resolution in this implementation; they are reserved for the future global config resolver.
 */
@Component
public class ConfigBackedAmountDisplayAdapter implements AmountDisplayPort {

    static final String FALLBACK_PATTERN = "#,##0.00";

    private final ConfigVariantResolver configVariantResolver;

    @Autowired
    public ConfigBackedAmountDisplayAdapter(ConfigVariantResolver configVariantResolver) {
        this.configVariantResolver = configVariantResolver;
    }

    ConfigBackedAmountDisplayAdapter(LocalizedConfigProperties amountFormatProperties) {
        this(new DefaultConfigVariantResolver(
                List.of(new LocalizedConfigSource(ConfigCategory.DISPLAY_AMOUNT_FORMAT, amountFormatProperties, "amount")),
                List.of(new DefaultConfigKeyCandidateStrategy(
                        new ConfigVariantCandidateGenerator(),
                        ConfigCategory.DISPLAY_AMOUNT_FORMAT))));
    }

    @Override
    public String formatAmount(BigDecimal amount, String lang, String accountEnv, String trustCode, String schemeType) {
        var value = amount == null ? BigDecimal.ZERO : amount;
        var pattern = resolvePattern(lang, accountEnv, trustCode, schemeType);
        return applyFormat(value, pattern);
    }

    String resolvePattern(String lang, String accountEnv, String trustCode, String schemeType) {
        var request = ConfigLookupRequest.optional(
                ConfigCategory.DISPLAY_AMOUNT_FORMAT,
                "amount",
                ConfigLookupContext.of(accountEnv, trustCode, schemeType, lang));

        return configVariantResolver.resolve(request).orElse(FALLBACK_PATTERN);
    }

    String resolvePattern(String lang, String schemeType) {
        return resolvePattern(lang, "", "", schemeType);
    }

    private String applyFormat(BigDecimal value, String pattern) {
        var symbols = new DecimalFormatSymbols(Locale.ENGLISH);
        var df = new DecimalFormat(pattern, symbols);
        df.setRoundingMode(RoundingMode.HALF_UP);
        return df.format(value);
    }
}


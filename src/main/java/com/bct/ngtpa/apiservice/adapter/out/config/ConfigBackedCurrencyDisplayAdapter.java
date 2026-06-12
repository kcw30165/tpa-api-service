package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.application.port.out.CurrencyDisplayPort;
import com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay;
import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import com.bct.ngtpa.apiservice.shared.config.ConfigLookupContext;
import com.bct.ngtpa.apiservice.shared.config.ConfigLookupRequest;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Component
public class ConfigBackedCurrencyDisplayAdapter implements CurrencyDisplayPort {

    private static final Locale ZH_HK_LOCALE = Locale.forLanguageTag("zh-HK");

    private final ConfigVariantResolver configVariantResolver;

    @Autowired
    public ConfigBackedCurrencyDisplayAdapter(ConfigVariantResolver configVariantResolver) {
        this.configVariantResolver = configVariantResolver;
    }

    ConfigBackedCurrencyDisplayAdapter(CurrencyMappingProperties currencyMappingProperties) {
        this(new DefaultConfigVariantResolver(
                List.of(new CurrencyMappingConfigSource(currencyMappingProperties)),
                List.of(new DefaultConfigKeyCandidateStrategy(
                        new ConfigVariantCandidateGenerator(),
                        ConfigCategory.CURRENCY_MAPPING))));
    }

    @Override
    public CurrencyDisplay resolveCurrencyDisplay(
            String code,
            String accountEnv,
            String trustCode,
            String schemeType) {
        if (!StringUtils.hasText(code)) {
            return new CurrencyDisplay("", "");
        }

        var normalizedCode = code.trim();
        return new CurrencyDisplay(
                resolve(normalizedCode, accountEnv, trustCode, schemeType, Locale.ENGLISH),
                resolve(normalizedCode, accountEnv, trustCode, schemeType, ZH_HK_LOCALE)
        );
    }

    private String resolve(String code, String accountEnv, String trustCode, String schemeType, Locale locale) {
        var request = ConfigLookupRequest.optional(
                ConfigCategory.CURRENCY_MAPPING,
                code,
                ConfigLookupContext.of(accountEnv, trustCode, schemeType, locale));

        return configVariantResolver.resolve(request).orElse(code);
    }
}

package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.application.port.out.DateDisplayPort;
import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import com.bct.ngtpa.apiservice.shared.config.ConfigLookupContext;
import com.bct.ngtpa.apiservice.shared.config.ConfigLookupRequest;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Config-backed implementation of {@link DateDisplayPort}.
 */
@Component
public class ConfigBackedDateDisplayAdapter implements DateDisplayPort {

    static final String FALLBACK_PATTERN = "dd/MM/yyyy";

    private final ConfigVariantResolver configVariantResolver;

    @Autowired
    public ConfigBackedDateDisplayAdapter(ConfigVariantResolver configVariantResolver) {
        this.configVariantResolver = configVariantResolver;
    }

    ConfigBackedDateDisplayAdapter(DateFormatProperties dateFormatProperties) {
        this(new DefaultConfigVariantResolver(
                List.of(new DateFormatConfigSource(dateFormatProperties)),
                List.of(new DefaultConfigKeyCandidateStrategy(
                        new ConfigVariantCandidateGenerator(),
                        ConfigCategory.DISPLAY_DATE_FORMAT))));
    }

    @Override
    public String formatDate(LocalDate date, String lang, String accountEnv, String trustCode, String schemeType) {
        if (date == null) {
            return "";
        }
        var pattern = resolvePattern(lang, accountEnv, trustCode, schemeType);
        return date.format(DateTimeFormatter.ofPattern(pattern));
    }

    String resolvePattern(String lang, String accountEnv, String trustCode, String schemeType) {
        var request = ConfigLookupRequest.optional(
                ConfigCategory.DISPLAY_DATE_FORMAT,
                "date",
                ConfigLookupContext.of(accountEnv, trustCode, schemeType, lang));

        return configVariantResolver.resolve(request).orElse(FALLBACK_PATTERN);
    }
}


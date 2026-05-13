package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import com.bct.ngtpa.apiservice.shared.config.ConfigKeyCandidateStrategy;
import com.bct.ngtpa.apiservice.shared.config.ConfigLookupRequest;
import com.bct.ngtpa.apiservice.shared.config.ConfigResolutionException;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CurrencyMappingKeyCandidateStrategy implements ConfigKeyCandidateStrategy {

    private static final String PREFIX = "currency-mapping";
    private static final String DEFAULT_LANGUAGE = "en";

    private final ConfigVariantCandidateGenerator candidateGenerator;

    @Override
    public ConfigCategory category() {
        return ConfigCategory.CURRENCY_MAPPING;
    }

    @Override
    public List<String> generateCandidateKeys(ConfigLookupRequest request) {
        if (!StringUtils.hasText(request.code())) {
            throw new ConfigResolutionException("Currency mapping code must not be blank");
        }

        var keys = new ArrayList<String>();
        var candidates = candidateGenerator.generate(request.context());
        appendLocaleKeys(keys, request.code(), request.context().localeKey(), candidates);
        if (!DEFAULT_LANGUAGE.equals(request.context().localeKey())) {
            appendLocaleKeys(keys, request.code(), DEFAULT_LANGUAGE, candidates);
        }

        Set<String> uniqueKeys = new LinkedHashSet<>(keys);
        return List.copyOf(uniqueKeys);
    }

    private void appendLocaleKeys(List<String> keys, String code, String language, List<String> candidates) {
        for (var candidate : candidates) {
            keys.add(PREFIX + "." + language + "." + code + "." + candidate);
        }
        keys.add(PREFIX + "." + language + "." + code);
    }
}
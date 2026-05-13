package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import com.bct.ngtpa.apiservice.shared.config.ConfigKeyCandidateStrategy;
import com.bct.ngtpa.apiservice.shared.config.ConfigLookupRequest;
import com.bct.ngtpa.apiservice.shared.config.ConfigResolutionException;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DisplayFormatKeyCandidateStrategy implements ConfigKeyCandidateStrategy {

    private static final String PREFIX = "display-format";
    private static final String DEFAULT_LANGUAGE = "en";
    private static final String DATE_CODE = "date";
    private static final String AMOUNT_CODE = "amount";
    private static final String WILDCARD_KEY = "*";

    private final ConfigVariantCandidateGenerator candidateGenerator;

    @Override
    public ConfigCategory category() {
        return ConfigCategory.DISPLAY_FORMAT;
    }

    @Override
    public List<String> generateCandidateKeys(ConfigLookupRequest request) {
        var code = validateCode(request.code());
        var candidates = candidateGenerator.generate(request.context());
        var keys = new ArrayList<String>();

        appendLocaleKeys(keys, code, request.context().localeKey(), candidates, true);
        if (!DEFAULT_LANGUAGE.equals(request.context().localeKey())) {
            appendLocaleKeys(keys, code, DEFAULT_LANGUAGE, candidates, true);
        }

        Set<String> uniqueKeys = new LinkedHashSet<>(keys);
        return List.copyOf(uniqueKeys);
    }

    private void appendLocaleKeys(
            List<String> keys,
            String code,
            String language,
            List<String> candidates,
            boolean includeWildcard) {
        for (var candidate : candidates) {
            keys.add(PREFIX + "." + code + "." + language + "." + candidate);
        }
        if (includeWildcard) {
            keys.add(PREFIX + "." + code + "." + language + "." + WILDCARD_KEY);
        }
    }

    private String validateCode(String code) {
        if (DATE_CODE.equals(code) || AMOUNT_CODE.equals(code)) {
            return code;
        }
        throw new ConfigResolutionException("Unsupported display-format code: " + code);
    }
}
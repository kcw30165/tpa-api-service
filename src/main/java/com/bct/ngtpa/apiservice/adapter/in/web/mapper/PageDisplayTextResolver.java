package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.shared.config.ConfigLookupContext;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class PageDisplayTextResolver {

    private static final String DEFAULT_LANGUAGE = Locale.ENGLISH.toString();

    private final ConfigVariantCandidateGenerator candidateGenerator;

    public PageDisplayTextResolver(ConfigVariantCandidateGenerator candidateGenerator) {
        this.candidateGenerator = candidateGenerator;
    }

    public String resolve(
            Map<String, Map<String, String>> display,
            String code,
            Map<String, String> inlineFallback,
            String language,
            String accountEnv,
            String trustCode,
            String schemeType) {
        if (!StringUtils.hasText(code)) {
            return resolveInlineFallback(inlineFallback, language);
        }

        var normalizedCode = code.trim();
        for (var languageKey : languageFallbackKeys(language)) {
            var resolved = resolveFromDisplay(
                    display,
                    languageKey,
                    normalizedCode,
                    accountEnv,
                    trustCode,
                    schemeType);
            if (StringUtils.hasText(resolved)) {
                return resolved;
            }
        }

        return resolveInlineFallback(inlineFallback, language);
    }

    private String resolveFromDisplay(
            Map<String, Map<String, String>> display,
            String languageKey,
            String code,
            String accountEnv,
            String trustCode,
            String schemeType) {
        if (display == null || !StringUtils.hasText(languageKey)) {
            return null;
        }

        var localizedDisplay = display.get(languageKey);
        if (localizedDisplay == null || localizedDisplay.isEmpty()) {
            return null;
        }

        for (var candidate : candidateKeys(code, accountEnv, trustCode, schemeType, languageKey)) {
            var value = localizedDisplay.get(candidate);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private List<String> candidateKeys(
            String code,
            String accountEnv,
            String trustCode,
            String schemeType,
            String languageKey) {
        var candidates = new LinkedHashSet<String>();
        var locale = Locale.forLanguageTag(languageKey.replace('_', '-'));
        var context = ConfigLookupContext.of(accountEnv, trustCode, schemeType, locale);
        for (var variant : candidateGenerator.generate(context)) {
            candidates.add(code + "." + variant);
        }
        candidates.add(code);
        return List.copyOf(candidates);
    }

    private String resolveInlineFallback(Map<String, String> inlineFallback, String language) {
        if (inlineFallback == null || inlineFallback.isEmpty()) {
            return "";
        }

        for (var languageKey : languageFallbackKeys(language)) {
            var value = inlineFallback.get(languageKey);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }

        return inlineFallback.values().stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private List<String> languageFallbackKeys(String language) {
        var keys = new LinkedHashSet<String>();
        if (StringUtils.hasText(language)) {
            var normalized = language.trim().replace('-', '_');
            keys.add(normalized);
            keys.add(Locale.forLanguageTag(normalized.replace('_', '-')).toString());
        }
        keys.add(DEFAULT_LANGUAGE);
        return List.copyOf(keys);
    }
}

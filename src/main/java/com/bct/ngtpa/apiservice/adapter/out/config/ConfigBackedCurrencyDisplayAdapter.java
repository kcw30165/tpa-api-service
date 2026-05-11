package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.application.dto.CurrencyDisplay;
import com.bct.ngtpa.apiservice.application.port.out.CurrencyDisplayPort;
import com.bct.ngtpa.apiservice.adapter.out.config.CurrencyMappingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ConfigBackedCurrencyDisplayAdapter implements CurrencyDisplayPort {

    private static final String EN_LOCALE = "en";
    private static final String ZH_HK_LOCALE = "zh_HK";

    private final CurrencyMappingProperties currencyMappingProperties;

    @Override
    public CurrencyDisplay resolveCurrencyDisplay(
            String code,
            String env,
            String trustCode,
            String schemeType) {
        return new CurrencyDisplay(
            resolve(EN_LOCALE, code, env, trustCode, schemeType),
            resolve(ZH_HK_LOCALE, code, env, trustCode, schemeType)
        );
    }

    private String resolve(String locale, String code, String env, String trustCode, String schemeType) {
        if (!StringUtils.hasText(code)) {
            return "";
        }

        var normalizedCode = code.trim();
        var localeMappings = currencyMappingProperties.getLocaleMappings(locale);

        for (var candidate : candidateKeys(normalizedCode, env, trustCode, schemeType)) {
            var mapped = localeMappings.get(candidate);
            if (StringUtils.hasText(mapped)) {
                return mapped;
            }
        }

        return normalizedCode;
    }

    private List<String> candidateKeys(String code, String env, String trustCode, String schemeType) {
        List<String> suffixSegments = new ArrayList<>();
        addIfPresent(suffixSegments, env);
        addIfPresent(suffixSegments, trustCode);
        addIfPresent(suffixSegments, schemeType);

        List<String> candidates = new ArrayList<>();
        for (int size = suffixSegments.size(); size > 0; size--) {
            candidates.add(code + "." + String.join(".", suffixSegments.subList(0, size)));
        }
        candidates.add(code);

        return List.copyOf(candidates);
    }

    private void addIfPresent(List<String> segments, String value) {
        if (StringUtils.hasText(value)) {
            segments.add(value.trim());
        }
    }
}

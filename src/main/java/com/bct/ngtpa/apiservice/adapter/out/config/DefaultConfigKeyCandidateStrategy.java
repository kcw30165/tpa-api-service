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
import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultConfigKeyCandidateStrategy implements ConfigKeyCandidateStrategy {

    private final ConfigVariantCandidateGenerator candidateGenerator;

    @Override
    public ConfigCategory category() {
        return ConfigCategory.ERROR_MESSAGE;
    }

    @Override
    public List<String> generateCandidateKeys(ConfigLookupRequest request) {
        if (!StringUtils.hasText(request.code())) {
            throw new ConfigResolutionException("Config code must not be blank");
        }

        var keys = new ArrayList<String>();
        for (var candidate : candidateGenerator.generate(request.context())) {
            keys.add(request.code() + "." + candidate);
        }
        keys.add(request.code());
        return List.copyOf(keys);
    }
}
package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import com.bct.ngtpa.apiservice.shared.config.ConfigKeyCandidateStrategy;
import com.bct.ngtpa.apiservice.shared.config.ConfigLookupRequest;
import com.bct.ngtpa.apiservice.shared.config.ConfigResolutionException;
import com.bct.ngtpa.apiservice.shared.config.ConfigSource;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DefaultConfigVariantResolver implements ConfigVariantResolver {

    private final Map<ConfigCategory, ConfigSource> sources;
    private final Map<ConfigCategory, ConfigKeyCandidateStrategy> strategies;

    public DefaultConfigVariantResolver(List<ConfigSource> sources, List<ConfigKeyCandidateStrategy> strategies) {
        this.sources = toSourceMap(sources);
        this.strategies = toStrategyMap(strategies);
    }

    @Override
    public Optional<String> resolve(ConfigLookupRequest request) {
        var source = sources.get(request.category());
        if (source == null) {
            throw new ConfigResolutionException("No config source registered for category " + request.category());
        }

        var strategy = strategies.get(request.category());
        if (strategy == null) {
            throw new ConfigResolutionException("No key candidate strategy registered for category " + request.category());
        }

        List<String> candidates;
        try {
            candidates = strategy.generateCandidateKeys(request);
        } catch (ConfigResolutionException exception) {
            if (request.required()) {
                throw exception;
            }
            return Optional.empty();
        }

        for (var candidate : candidates) {
            var resolved = source.get(candidate, request.context().locale())
                    .filter(StringUtils::hasText);
            if (resolved.isPresent()) {
                return resolved;
            }
        }

        if (request.required()) {
            throw new ConfigResolutionException(
                    "Required config not found for category " + request.category() + " and code " + request.code());
        }
        return Optional.empty();
    }

    private static Map<ConfigCategory, ConfigSource> toSourceMap(List<ConfigSource> sources) {
        var result = new EnumMap<ConfigCategory, ConfigSource>(ConfigCategory.class);
        for (var source : sources) {
            result.put(source.category(), source);
        }
        return Map.copyOf(result);
    }

    private static Map<ConfigCategory, ConfigKeyCandidateStrategy> toStrategyMap(
            List<ConfigKeyCandidateStrategy> strategies) {
        var result = new EnumMap<ConfigCategory, ConfigKeyCandidateStrategy>(ConfigCategory.class);
        for (var strategy : strategies) {
            result.put(strategy.category(), strategy);
        }
        return Map.copyOf(result);
    }
}
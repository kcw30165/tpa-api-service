package com.bct.ngtpa.apiservice.shared.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ConfigVariantCandidateGenerator {

    public List<String> generate(ConfigLookupContext context) {
        var candidates = new ArrayList<String>();

        addJoined(candidates, context.accountEnv(), context.trustCode(), context.schemeType());
        addJoined(candidates, context.accountEnv(), context.trustCode());
        addJoined(candidates, context.accountEnv(), context.schemeType());
        addJoined(candidates, context.trustCode(), context.schemeType());
        addSingle(candidates, context.accountEnv());
        addSingle(candidates, context.trustCode());
        addSingle(candidates, context.schemeType());

        Set<String> uniqueCandidates = new LinkedHashSet<>(candidates);
        return List.copyOf(uniqueCandidates);
    }

    private void addJoined(List<String> candidates, String... segments) {
        var normalizedSegments = new ArrayList<String>(segments.length);
        for (var segment : segments) {
            if (segment == null || segment.isBlank()) {
                return;
            }
            normalizedSegments.add(segment.trim());
        }
        candidates.add(String.join(".", normalizedSegments));
    }

    private void addSingle(List<String> candidates, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        candidates.add(value.trim());
    }
}
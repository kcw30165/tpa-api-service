package com.bct.ngtpa.apiservice.shared.config;

import java.util.List;

public interface ConfigKeyCandidateStrategy {

    ConfigCategory category();

    List<String> generateCandidateKeys(ConfigLookupRequest request);
}
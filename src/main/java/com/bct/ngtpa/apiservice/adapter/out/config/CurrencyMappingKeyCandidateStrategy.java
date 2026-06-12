package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigCategory;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;

/**
 * @deprecated Currency mapping now uses {@link DefaultConfigKeyCandidateStrategy}
 * with {@link ConfigCategory#CURRENCY_MAPPING}. This compatibility wrapper keeps
 * constructor-level tests working until redundant candidate strategies are removed.
 */
@Deprecated(forRemoval = true)
public class CurrencyMappingKeyCandidateStrategy extends DefaultConfigKeyCandidateStrategy {

    public CurrencyMappingKeyCandidateStrategy(ConfigVariantCandidateGenerator candidateGenerator) {
        super(candidateGenerator, ConfigCategory.CURRENCY_MAPPING);
    }
}

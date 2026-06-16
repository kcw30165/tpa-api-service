package com.bct.ngtpa.apiservice.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bct.ngtpa.apiservice.shared.config.ConfigLookupContext;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ConfigVariantResolverConfigurationTest {

    @Test
    void createsConfigVariantCandidateGeneratorBean() {
        var generator = new ConfigVariantResolverConfiguration().configVariantCandidateGenerator();

        assertNotNull(generator);
        assertEquals(List.of("JP.JPM.OE", "JP.JPM", "JP.OE", "JP", "OE"),
                generator.generate(ConfigLookupContext.of("JP", "JPM", "OE", Locale.ENGLISH)));
    }
}

package com.bct.ngtpa.apiservice.adapter.out.config;

import com.bct.ngtpa.apiservice.shared.config.ConfigVariantResolver;

/**
 * @deprecated Error message resolution is now implemented by
 * {@link ConfigBackedErrorMessageResolver}. This wrapper is kept temporarily for
 * source compatibility and can be removed with the other redundant compatibility
 * types in the cleanup step.
 */
@Deprecated(forRemoval = true)
public class ConfiguredErrorMessageResolver extends ConfigBackedErrorMessageResolver {

    static final String HARD_CODED_FALLBACK = ConfigBackedErrorMessageResolver.HARD_CODED_FALLBACK;

    public ConfiguredErrorMessageResolver(ConfigVariantResolver configVariantResolver) {
        super(configVariantResolver);
    }
}

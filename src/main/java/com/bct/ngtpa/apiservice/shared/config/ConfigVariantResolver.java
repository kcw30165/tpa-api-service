package com.bct.ngtpa.apiservice.shared.config;

import java.util.Optional;

public interface ConfigVariantResolver {

    Optional<String> resolve(ConfigLookupRequest request);
}
package com.bct.ngtpa.apiservice.adapter.out.configservice.bootstrap;

import java.util.List;

/**
 * Bootstrap-time fetch seam.
 *
 * <p>Exists so the post-processor's fail-soft behavior can be exercised without a live HTTP
 * endpoint. The production implementation is {@link ConfigServiceBootstrapClient}.
 */
@FunctionalInterface
public interface ConfigServiceBootstrapFetcher {

  List<ConfigServiceBootstrapEntry> fetch(ConfigServiceBootstrapSettings settings);
}

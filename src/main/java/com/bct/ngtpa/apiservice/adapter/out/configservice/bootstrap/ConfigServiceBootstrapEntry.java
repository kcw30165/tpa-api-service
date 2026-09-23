package com.bct.ngtpa.apiservice.adapter.out.configservice.bootstrap;

/**
 * One configuration key/value pair as returned by {@code GET /api/configs}.
 *
 * <p>Deliberately separate from {@code ConfigServiceResponse}: that type is a Spring-managed
 * runtime DTO, and this package runs before the application context exists. See the bootstrap
 * constraint in docs/capabilities/config-service-startup-loader/implementation-plan.md.
 *
 * <p>{@code toString} omits the value because configuration values may themselves be sensitive.
 */
public record ConfigServiceBootstrapEntry(String configKey, String configValue) {

  @Override
  public String toString() {
    return "ConfigServiceBootstrapEntry[configKey=" + configKey + ", configValue=<redacted>]";
  }
}

package com.bct.ngtpa.apiservice.adapter.out.configservice.bootstrap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.MapPropertySource;

/** Turns Config Service entries into a named Spring {@link MapPropertySource}. */
public final class ConfigServicePropertySourceFactory {

  public static final String SOURCE_NAME = "ngtpaConfigService";

  private ConfigServicePropertySourceFactory() {}

  /**
   * Blank keys are discarded. Blank values are preserved, because an intentionally empty
   * configuration value is meaningful. On duplicate keys the last entry wins.
   */
  public static MapPropertySource create(List<ConfigServiceBootstrapEntry> entries) {
    Map<String, Object> properties = new LinkedHashMap<>();
    if (entries != null) {
      for (ConfigServiceBootstrapEntry entry : entries) {
        if (entry == null || entry.configKey() == null || entry.configKey().isBlank()) {
          continue;
        }
        properties.put(
            entry.configKey().trim(), entry.configValue() == null ? "" : entry.configValue());
      }
    }
    return new MapPropertySource(SOURCE_NAME, properties);
  }
}

package com.bct.ngtpa.apiservice.adapter.out.configservice.bootstrap;

import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Loads configuration from the NGTPA Config Service at startup and registers it as a property
 * source, so any {@code @ConfigurationProperties} class can be backed by Config Service without
 * per-request I/O.
 *
 * <p><strong>Fail-soft (D1).</strong> No failure here may prevent the application from starting.
 * Every error path logs a warning and leaves {@code application.yml} defaults in place.
 *
 * <p><strong>Precedence (D2).</strong> Registered with {@code addFirst}, so a Config Service value
 * beats an {@code application.yml} default. There is no OS-environment override tier.
 *
 * <p>Uses {@code commons-logging} because Spring Boot's logging system is not initialised this
 * early.
 */
public class ConfigServiceEnvironmentPostProcessor implements EnvironmentPostProcessor {

  private static final Log LOG = LogFactory.getLog(ConfigServiceEnvironmentPostProcessor.class);

  private final ConfigServiceBootstrapFetcher fetcher;

  public ConfigServiceEnvironmentPostProcessor() {
    this(new ConfigServiceBootstrapClient());
  }

  ConfigServiceEnvironmentPostProcessor(ConfigServiceBootstrapFetcher fetcher) {
    this.fetcher = fetcher;
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {

    ConfigServiceBootstrapSettings settings = ConfigServiceBootstrapSettings.from(environment);

    if (!settings.enabled()) {
      LOG.info(
          "event=config-service.bootstrap.skipped reason=base-url-absent "
              + "detail=application will use application.yml defaults");
      return;
    }

    if (environment.getPropertySources().contains(ConfigServicePropertySourceFactory.SOURCE_NAME)) {
      return;
    }

    LOG.info(
        "event=config-service.bootstrap.start application="
            + settings.application()
            + " profile="
            + settings.profile()
            + " label="
            + settings.label());

    List<ConfigServiceBootstrapEntry> entries;
    try {
      entries = fetcher.fetch(settings);
    } catch (RuntimeException exception) {
      // Fail-soft: never block startup. Message only -- never the response body.
      LOG.warn(
          "event=config-service.bootstrap.failed reason="
              + exception.getClass().getSimpleName()
              + " detail=application is running on application.yml defaults");
      return;
    }

    if (entries == null || entries.isEmpty()) {
      LOG.info(
          "event=config-service.bootstrap.empty "
              + "detail=no configuration returned for this partition");
      return;
    }

    MapPropertySource source = ConfigServicePropertySourceFactory.create(entries);
    environment.getPropertySources().addFirst(source);

    // Count only. Key names are DEBUG; values are never logged.
    LOG.info("event=config-service.bootstrap.loaded keyCount=" + source.getPropertyNames().length);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
          "event=config-service.bootstrap.keys names="
              + String.join(",", source.getPropertyNames()));
    }
  }
}

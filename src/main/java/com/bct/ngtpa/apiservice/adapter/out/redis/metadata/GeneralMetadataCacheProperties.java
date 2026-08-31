package com.bct.ngtpa.apiservice.adapter.out.redis.metadata;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redis-cache.general-metadata")
public record GeneralMetadataCacheProperties(Duration ttl) {
  public GeneralMetadataCacheProperties {
    ttl = Objects.requireNonNullElse(ttl, Duration.parse("PT20M"));
    if (ttl.isZero() || ttl.isNegative())
      throw new IllegalArgumentException("General metadata cache TTL must be positive");
  }
}

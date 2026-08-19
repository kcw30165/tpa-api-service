package com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility;

import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPersistenceCompatibilityPort;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public final class AuthFlowSessionPersistenceCompatibilityStartupGate implements ApplicationRunner {
  private final AuthFlowSessionPersistenceCompatibilityPort port;
  private final AuthFlowSessionPersistenceSchemaProperties properties;
  private final AuthFlowSessionPersistenceCompatibilityState state;
  private final Duration timeout;

  public AuthFlowSessionPersistenceCompatibilityStartupGate(
      AuthFlowSessionPersistenceCompatibilityPort port,
      AuthFlowSessionPersistenceSchemaProperties properties,
      AuthFlowSessionPersistenceCompatibilityState state,
      Duration timeout) {
    this.port = Objects.requireNonNull(port);
    this.properties = Objects.requireNonNull(properties);
    this.state = Objects.requireNonNull(state);
    this.timeout = Objects.requireNonNull(timeout);
  }

  @Override
  public void run(ApplicationArguments args) {
    int configuredVersion = properties.requiredVersion();
    var result = port.establish(configuredVersion).block(timeout);
    if (result == null || !result.compatible()) {
      String actual =
          result == null || result.actualVersion() == null
              ? "unavailable"
              : result.actualVersion().toString();
      throw new IllegalStateException(
          "auth-flow session persistence schema is incompatible expectedVersion=2 actualVersion="
              + actual);
    }
    state.establish();
  }
}

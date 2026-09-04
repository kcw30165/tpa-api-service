package com.bct.ngtpa.apiservice.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ApplicationClockConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(ApplicationClockConfig.class);

  @Test
  void providesExactlyOneUtcApplicationClock() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(Clock.class);
          assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
        });
  }

  @Test
  void preservesTestControlledClockOverride() {
    Clock fixed = Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC);
    runner
        .withBean(Clock.class, () -> fixed)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(Clock.class);
              assertThat(context.getBean(Clock.class)).isSameAs(fixed);
            });
  }
}

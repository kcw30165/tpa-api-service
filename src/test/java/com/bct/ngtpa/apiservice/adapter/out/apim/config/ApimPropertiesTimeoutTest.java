package com.bct.ngtpa.apiservice.adapter.out.apim.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ApimPropertiesTimeoutTest {

  @Test
  void defaultsToTenThousandMilliseconds() {
    assertThat(new ApimProperties().validatedTimeoutMilliseconds()).isEqualTo(10000);
  }

  @Test
  void bindsTimeoutMillisecondsFromConfiguration() {
    Binder binder =
        new Binder(new MapConfigurationPropertySource(Map.of("apim.timeout-milliseconds", "5000")));
    ApimProperties bound =
        binder
            .bind("apim", Bindable.of(ApimProperties.class))
            .orElseThrow(() -> new IllegalStateException("Failed to bind ApimProperties"));

    assertThat(bound.validatedTimeoutMilliseconds()).isEqualTo(5000);
  }

  @Test
  void rejectsZeroAndNegativeTimeouts() {
    ApimProperties zero = new ApimProperties();
    zero.setTimeoutMilliseconds(0);
    assertThatThrownBy(zero::validatedTimeoutMilliseconds)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("greater than zero");

    ApimProperties negative = new ApimProperties();
    negative.setTimeoutMilliseconds(-1);
    assertThatThrownBy(negative::validatedTimeoutMilliseconds)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("greater than zero");
  }
}

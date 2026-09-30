package com.bct.ngtpa.apiservice.adapter.out.apim.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.util.unit.DataSize;

class ApimPropertiesMaxResponseSizeTest {

  @Test
  void defaultsToSixteenMegabytes() {
    assertThat(new ApimProperties().maxResponseSizeBytes()).isEqualTo(16 * 1024 * 1024);
  }

  @Test
  void bindsDataSizeFromConfiguration() {
    Binder binder =
        new Binder(new MapConfigurationPropertySource(Map.of("apim.max-response-size", "32MB")));
    ApimProperties bound =
        binder
            .bind("apim", Bindable.of(ApimProperties.class))
            .orElseThrow(() -> new IllegalStateException("Failed to bind ApimProperties"));

    assertThat(bound.maxResponseSizeBytes()).isEqualTo(32 * 1024 * 1024);
  }

  @Test
  void rejectsZeroNullAndOversizedLimits() {
    ApimProperties zero = new ApimProperties();
    zero.setMaxResponseSize(DataSize.ofBytes(0));
    assertThatThrownBy(zero::maxResponseSizeBytes)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("greater than zero");

    ApimProperties missing = new ApimProperties();
    missing.setMaxResponseSize(null);
    assertThatThrownBy(missing::maxResponseSizeBytes)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("greater than zero");

    ApimProperties oversized = new ApimProperties();
    oversized.setMaxResponseSize(DataSize.ofGigabytes(4));
    assertThatThrownBy(oversized::maxResponseSizeBytes)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must not exceed");
  }
}

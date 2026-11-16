package com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

class VestingScaleApimConfigurationTest {

  @Test
  void bindsTheVestingScalePath() {
    var binder =
        new Binder(
            new MapConfigurationPropertySource(Map.of("apim.vesting-scale.path", "/ws/bound")));

    VestingScaleApimProperties bound =
        binder
            .bind("apim.vesting-scale", Bindable.of(VestingScaleApimProperties.class))
            .orElseThrow(
                () -> new IllegalStateException("Failed to bind VestingScaleApimProperties"));

    assertThat(bound.getPath()).isEqualTo("/ws/bound");
  }

  @Test
  void validatesAndTrimsThePath() {
    var properties = new VestingScaleApimProperties();
    properties.setPath(" /ws/NGTPA/v1/TRPGetVestingScale ");

    properties.validate();

    assertThat(properties.getPath()).isEqualTo("/ws/NGTPA/v1/TRPGetVestingScale");
  }

  @Test
  void rejectsMissingOrBlankPathsAtStartupValidation() {
    assertThatThrownBy(() -> new VestingScaleApimValidationConfig(new VestingScaleApimProperties()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apim.vesting-scale.path");

    var blank = new VestingScaleApimProperties();
    blank.setPath("   ");
    assertThatThrownBy(() -> new VestingScaleApimValidationConfig(blank))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apim.vesting-scale.path");
  }

  @Test
  void shippedApplicationYmlDefinesThePath() throws IOException {
    String yaml =
        new ClassPathResource("application.yml").getContentAsString(StandardCharsets.UTF_8);

    assertThat(yaml)
        .contains(
            "\n  vesting-scale:\n"
                + "    path: ${APIM_VESTING_SCALE_PATH:/ws/NGTPA/v1/TRPGetVestingScale}\n");
  }

  @Test
  void environmentVariableOverridesTheYamlDefault() {
    new ApplicationContextRunner()
        .withInitializer(
            context -> {
              try {
                var yaml =
                    new YamlPropertySourceLoader()
                        .load("application", new ClassPathResource("application.yml"))
                        .getFirst();
                context.getEnvironment().getPropertySources().addLast(yaml);
              } catch (IOException exception) {
                throw new UncheckedIOException(exception);
              }
            })
        .withPropertyValues("APIM_VESTING_SCALE_PATH=/ws/environment-override")
        .withUserConfiguration(VestingScaleApimValidationConfig.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(VestingScaleApimProperties.class).getPath())
                  .isEqualTo("/ws/environment-override");
            });
  }
}

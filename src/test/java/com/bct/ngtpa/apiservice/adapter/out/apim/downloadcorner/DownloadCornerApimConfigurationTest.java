package com.bct.ngtpa.apiservice.adapter.out.apim.downloadcorner;

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

class DownloadCornerApimConfigurationTest {

  @Test
  void bindsTheDownloadCornerPath() {
    var binder =
        new Binder(
            new MapConfigurationPropertySource(Map.of("apim.download-corner.path", "/ws/bound")));

    DownloadCornerApimProperties bound =
        binder
            .bind("apim.download-corner", Bindable.of(DownloadCornerApimProperties.class))
            .orElseThrow(
                () -> new IllegalStateException("Failed to bind DownloadCornerApimProperties"));

    assertThat(bound.getPath()).isEqualTo("/ws/bound");
  }

  @Test
  void validatesAndTrimsThePath() {
    var properties = new DownloadCornerApimProperties();
    properties.setPath(" /ws/NGTPA/v1/TRPGetDownloadCorner ");

    properties.validate();

    assertThat(properties.getPath()).isEqualTo("/ws/NGTPA/v1/TRPGetDownloadCorner");
  }

  @Test
  void rejectsMissingOrBlankPathsAtStartupValidation() {
    assertThatThrownBy(
            () -> new DownloadCornerApimValidationConfig(new DownloadCornerApimProperties()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apim.download-corner.path");

    var blank = new DownloadCornerApimProperties();
    blank.setPath("   ");
    assertThatThrownBy(() -> new DownloadCornerApimValidationConfig(blank))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("apim.download-corner.path");
  }

  @Test
  void shippedApplicationYmlDefinesThePath() throws IOException {
    String yaml =
        new ClassPathResource("application.yml").getContentAsString(StandardCharsets.UTF_8);

    assertThat(yaml)
        .contains(
            "\n  download-corner:\n"
                + "    path: ${APIM_DOWNLOAD_CORNER_PATH:/ws/NGTPA/v1/TRPGetDownloadCorner}\n");
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
        .withPropertyValues("APIM_DOWNLOAD_CORNER_PATH=/ws/environment-override")
        .withUserConfiguration(DownloadCornerApimValidationConfig.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(DownloadCornerApimProperties.class).getPath())
                  .isEqualTo("/ws/environment-override");
            });
  }
}

package com.bct.ngtpa.apiservice.adapter.out.apim.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.redis.metadata.GeneralMetadataCacheProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class GeneralMetadataConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(GeneralMetadataApimValidationConfig.class, CacheConfig.class);

  @Test
  void bindsConfiguredPathAndSharedTtl() {
    runner
        .withPropertyValues(
            "apim.general-metadata.path=/override/metadata",
            "redis-cache.general-metadata.ttl=PT7M")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(GeneralMetadataApimProperties.class).getPath())
                  .isEqualTo("/override/metadata");
              assertThat(context.getBean(GeneralMetadataCacheProperties.class).ttl())
                  .isEqualTo(Duration.ofMinutes(7));
            });
  }

  @Test
  void blankOrMissingPathFailsStartup() {
    runner
        .withPropertyValues("apim.general-metadata.path= ")
        .run(context -> assertThat(context).hasFailed());
    runner.run(context -> assertThat(context).hasFailed());
  }

  @Test
  void zeroAndNegativeTtlFailBinding() {
    runner
        .withPropertyValues(
            "apim.general-metadata.path=/x", "redis-cache.general-metadata.ttl=PT0S")
        .run(context -> assertThat(context).hasFailed());
    runner
        .withPropertyValues(
            "apim.general-metadata.path=/x", "redis-cache.general-metadata.ttl=-PT1S")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void yamlOwnsExplicitRuntimeValuesAndOldPrefixIsAbsent() throws Exception {
    String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
    assertThat(yaml)
        .contains("general-metadata:", "path: ${APIM_GENERAL_METADATA_PATH:", "ttl: PT20M")
        .doesNotContain("account-balance.general-metadata.cache");
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties({
    GeneralMetadataApimProperties.class,
    GeneralMetadataCacheProperties.class
  })
  static class TestConfig {}

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(GeneralMetadataCacheProperties.class)
  static class CacheConfig {}

}

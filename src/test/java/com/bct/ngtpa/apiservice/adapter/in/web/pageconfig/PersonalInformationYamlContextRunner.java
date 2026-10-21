package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.io.IOException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * Test support: an application context runner that binds the real personal-information page YAML.
 */
public final class PersonalInformationYamlContextRunner {

  public static final String YAML_RESOURCE = "application-page-personal-information.yml";

  private PersonalInformationYamlContextRunner() {}

  public static ApplicationContextRunner create() {
    return new ApplicationContextRunner()
        .withUserConfiguration(TestConfig.class)
        .withInitializer(
            context -> {
              try {
                new YamlPropertySourceLoader()
                    .load(YAML_RESOURCE, new ClassPathResource(YAML_RESOURCE))
                    .forEach(
                        source -> context.getEnvironment().getPropertySources().addLast(source));
              } catch (IOException ex) {
                throw new IllegalStateException("Failed to load YAML", ex);
              }
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(BffPagesProperties.class)
  static class TestConfig {}
}

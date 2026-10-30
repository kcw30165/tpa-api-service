package com.bct.ngtpa.apiservice.adapter.out.apim.passwordvalidation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

class PasswordValidationApimConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withInitializer(
              context -> {
                try {
                  new YamlPropertySourceLoader()
                      .load("application", new ClassPathResource("application.yml"))
                      .forEach(context.getEnvironment().getPropertySources()::addLast);
                } catch (IOException exception) {
                  throw new IllegalStateException(exception);
                }
              })
          .withUserConfiguration(PasswordValidationApimValidationConfig.class);

  @Test
  void bindsDefaultPath() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context.getBean(PasswordValidationApimProperties.class).getPath())
              .isEqualTo("/ws/NGTPA/v1/TRPPwdValidation");
        });
  }

  @Test
  void bindsConfiguredOverride() {
    runner
        .withPropertyValues("apim.password-validation.path=/configured/password-validation")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(PasswordValidationApimProperties.class).getPath())
                  .isEqualTo("/configured/password-validation");
            });
  }
}

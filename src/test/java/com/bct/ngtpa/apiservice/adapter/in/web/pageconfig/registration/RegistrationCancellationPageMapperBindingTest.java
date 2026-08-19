package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.application.dto.workflow.WorkflowNextStep;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

class RegistrationCancellationPageMapperBindingTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          .withInitializer(
              context -> {
                var resource = new ClassPathResource("application-page-registration.yml");
                try {
                  var sources = new YamlPropertySourceLoader().load("registrationPage", resource);
                  sources.forEach(
                      source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException exception) {
                  throw new IllegalStateException(
                      "Failed to load registration page YAML", exception);
                }
              });

  @Test
  void bindsStandaloneCancellationPageWithoutVariant() {
    contextRunner.run(
        context -> {
          var page =
              context.getBean(BffPagesProperties.class).getPages().get("registration-cancelled");
          assertNotNull(page);
          assertNotNull(page.getMetadata());
          assertEquals("registration-cancelled", page.getMetadata().getId());
          assertEquals("registration.cancelled.title", page.getMetadata().getTitleCode());
          assertNotNull(page.getDisplay());
          assertFalse(page.getDisplay().isEmpty());
          assertNotNull(page.getForm());
          assertNotNull(page.getForm().getActions());
          assertFalse(page.getForm().getActions().isEmpty());
          assertNotNull(page.getVariants());
          assertEquals(0, page.getVariants().size());
        });
  }

  @Test
  void mapsCancellationPageToLanguageIndependentSemanticContract() {
    contextRunner.run(
        context -> {
          var mapper = mapper(context.getBean(BffPagesProperties.class));
          var english = mapper.map("en");
          var chinese = mapper.map("zh-HK");

          assertEquals(english, chinese);
          assertEquals("registration-cancelled", english.pageId());
          assertEquals(1, english.actions().size());
          assertEquals("returnToLogin", english.actions().getFirst().id());
          assertEquals("PRIMARY", english.actions().getFirst().type());
          assertEquals(WorkflowNextStep.RETURN_TO_LOGIN, english.actions().getFirst().nextStep());
        });
  }

  private RegistrationCancellationPageMapper mapper(BffPagesProperties properties) {
    return new RegistrationCancellationPageMapper(properties);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(BffPagesProperties.class)
  static class TestConfig {}
}

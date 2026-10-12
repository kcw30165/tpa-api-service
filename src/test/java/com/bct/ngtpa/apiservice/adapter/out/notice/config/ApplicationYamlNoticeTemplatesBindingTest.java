package com.bct.ngtpa.apiservice.adapter.out.notice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Loads the REAL {@code src/main/resources/application.yml} from the test classpath (not a
 * synthetic property map, unlike {@link NoticeTemplateServiceConfigTest}) via Spring Boot's
 * standard {@link YamlPropertySourceLoader} + {@link Binder} combination, and confirms the
 * notice-templates.* content populated in Phase I Stage I-5 both binds and passes {@link
 * NoticeTemplateProperties#validate()}. This is a lightweight binding check only; it does not start
 * a Spring ApplicationContext. The full per-combination (4 message types x 2 channels x 2 locales)
 * resolve() correctness smoke test is Phase I Stage I-6, not duplicated here.
 */
class ApplicationYamlNoticeTemplatesBindingTest {

  @Test
  void realApplicationYamlNoticeTemplatesSectionBindsAndValidates() throws Exception {
    YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
    List<PropertySource<?>> sources =
        loader.load("application.yml", new ClassPathResource("application.yml"));
    MutablePropertySources propertySources = new MutablePropertySources();
    sources.forEach(propertySources::addLast);

    Binder binder = new Binder(ConfigurationPropertySources.from(propertySources));
    NoticeTemplateProperties properties =
        binder
            .bind("notice-templates", NoticeTemplateProperties.class)
            .orElseThrow(
                () -> new AssertionError("notice-templates did not bind from application.yml"));

    properties.validate();

    assertThat(properties.getDefinitions())
        .containsKeys("registration-otp", "login-otp", "forgot-password-otp", "username-recovery");
  }
}

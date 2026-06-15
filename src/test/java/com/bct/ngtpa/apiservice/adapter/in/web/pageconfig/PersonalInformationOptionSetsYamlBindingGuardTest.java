package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

class PersonalInformationOptionSetsYamlBindingGuardTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withInitializer(context -> {
                var resource = new ClassPathResource("application-page-personal-information.yml");
                try {
                    var propertySources = new YamlPropertySourceLoader()
                            .load("personalInformationPage", resource);
                    propertySources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("Failed to load personal information YAML", ex);
                }
            });

    @Test
    void bindsSmsLanguagesOptionSetFromPersonalInformationYaml() {
        contextRunner.run(context -> {
            BffPagesProperties properties = context.getBean(BffPagesProperties.class);
            PageSchemaProperties page = properties.getPages().get("personalInformation");

            assertThat(page).isNotNull();
            assertThat(page.getForm()).isNotNull();
            assertThat(page.getForm().getOptionSets()).containsOnlyKeys("smsLanguages");

            OptionSetProperties smsLanguages = page.getForm().getOptionSets().get("smsLanguages");
            assertThat(smsLanguages.getCode()).isEqualTo("smsLanguages");
            assertThat(smsLanguages.getOptions()).containsOnlyKeys("smsLanguages");
            assertThat(smsLanguages.getOptions().get("smsLanguages"))
                    .extracting(option -> option.getValue() + ":" + option.getLabelCode())
                    .containsExactly(
                            "en:personalInformation.option.smsLanguage.en",
                            "zh_HK:personalInformation.option.smsLanguage.zh_HK");
        });
    }

    @Test
    void smsLanguageFieldReferencesTheBoundSmsLanguagesOptionSet() {
        contextRunner.run(context -> {
            BffPagesProperties properties = context.getBean(BffPagesProperties.class);
            PageSchemaProperties page = properties.getPages().get("personalInformation");

            FieldProperties smsLanguage = page.getForm().getSections().stream()
                    .filter(section -> "communicationSettings".equals(section.getId()))
                    .flatMap(section -> section.getFields() == null ? java.util.stream.Stream.empty() : section.getFields().stream())
                    .filter(field -> "smsLanguage".equals(field.getId()))
                    .findFirst()
                    .orElseThrow();

            assertThat(smsLanguage.getControlType()).isEqualTo("radio");
            assertThat(smsLanguage.getOptionSource()).isEqualTo("smsLanguages");
            assertThat(page.getForm().getOptionSets()).containsKey(smsLanguage.getOptionSource());
        });
    }

    @Test
    void smsLanguageOptionDisplayCodesAreDeclaredForBothSupportedLanguages() {
        contextRunner.run(context -> {
            BffPagesProperties properties = context.getBean(BffPagesProperties.class);
            PageSchemaProperties page = properties.getPages().get("personalInformation");

            assertThat(page.getDisplay()).containsKeys(
                    "personalInformation.option.smsLanguage.en",
                    "personalInformation.option.smsLanguage.zh_HK");
            assertThat(page.getDisplay().get("personalInformation.option.smsLanguage.en"))
                    .containsEntry("en", "English")
                    .containsEntry("zh_HK", "英文");
            assertThat(page.getDisplay().get("personalInformation.option.smsLanguage.zh_HK"))
                    .containsEntry("en", "Traditional Chinese")
                    .containsEntry("zh_HK", "繁體中文");
        });
    }

    @Test
    void optionSetBaseCodeIsTheFallbackKeyAndNoUnexpectedLanguageOptionsAreDeclared() {
        contextRunner.run(context -> {
            BffPagesProperties properties = context.getBean(BffPagesProperties.class);
            PageSchemaProperties page = properties.getPages().get("personalInformation");
            OptionSetProperties smsLanguages = page.getForm().getOptionSets().get("smsLanguages");

            assertThat(smsLanguages.getOptions()).containsOnlyKeys(smsLanguages.getCode());
            assertThat(smsLanguages.getOptions().get(smsLanguages.getCode()))
                    .extracting(OptionProperties::getValue)
                    .containsExactlyElementsOf(List.of("en", "zh_HK"));
        });
    }

    @Configuration
    @EnableConfigurationProperties(BffPagesProperties.class)
    static class TestConfig {
    }
}

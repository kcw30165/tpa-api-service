package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormPageResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormSchemaResponse;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationResult;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

class PersonalInformationFormOptionSetsRuntimeSerializationGuardTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
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
    void realYamlAndMapperSerializeEnglishSmsLanguagesOptionSetInFormResponse() {
        contextRunner.run(context -> {
            FormPageResponse<FormSchemaResponse> response = mapper(context.getBean(BffPagesProperties.class))
                    .toFormPageResponse(
                            new PersonalInformationResult(
                                    Map.of("sms-language", "en"),
                                    Map.of("sms-language", "EDITABLE")),
                            "en",
                            "DB",
                            "TRUST",
                            "MPF");

            JsonNode json = toJson(response);

            assertThat(json.path("form").path("optionSets").has("smsLanguages")).isTrue();
            JsonNode smsLanguages = json.path("form").path("optionSets").path("smsLanguages");
            assertThat(smsLanguages.isArray()).isTrue();
            assertThat(smsLanguages).hasSize(2);
            assertThat(smsLanguages.get(0).path("value").asText()).isEqualTo("en");
            assertThat(smsLanguages.get(0).path("text").asText()).isEqualTo("English");
            assertThat(smsLanguages.get(1).path("value").asText()).isEqualTo("zh_HK");
            assertThat(smsLanguages.get(1).path("text").asText()).isEqualTo("Traditional Chinese");
        });
    }

    @Test
    void realYamlAndMapperSerializeTraditionalChineseSmsLanguagesOptionSetInFormResponse() {
        contextRunner.run(context -> {
            FormPageResponse<FormSchemaResponse> response = mapper(context.getBean(BffPagesProperties.class))
                    .toFormPageResponse(
                            new PersonalInformationResult(
                                    Map.of("sms-language", "zh_HK"),
                                    Map.of("sms-language", "EDITABLE")),
                            "zh_HK",
                            "DB",
                            "TRUST",
                            "MPF");

            JsonNode json = toJson(response);

            JsonNode smsLanguages = json.path("form").path("optionSets").path("smsLanguages");
            assertThat(smsLanguages).hasSize(2);
            assertThat(smsLanguages.get(0).path("value").asText()).isEqualTo("en");
            assertThat(smsLanguages.get(0).path("text").asText()).isEqualTo("英文");
            assertThat(smsLanguages.get(1).path("value").asText()).isEqualTo("zh_HK");
            assertThat(smsLanguages.get(1).path("text").asText()).isEqualTo("繁體中文");
        });
    }

    @Test
    void serializedSmsLanguageFieldOptionSourceMatchesSerializedOptionSetKey() {
        contextRunner.run(context -> {
            FormPageResponse<FormSchemaResponse> response = mapper(context.getBean(BffPagesProperties.class))
                    .toFormPageResponse(
                            new PersonalInformationResult(
                                    Map.of("sms-language", "en"),
                                    Map.of("sms-language", "EDITABLE")),
                            "en",
                            "DB",
                            "TRUST",
                            "MPF");

            JsonNode json = toJson(response);
            JsonNode communicationSettings = findSection(json, "communicationSettings");
            JsonNode smsLanguage = findField(communicationSettings, "smsLanguage");
            String optionSource = smsLanguage.path("optionSource").asText();

            assertThat(optionSource).isEqualTo("smsLanguages");
            assertThat(json.path("form").path("optionSets").has(optionSource)).isTrue();
        });
    }

    private PersonalInformationWebMapper mapper(BffPagesProperties properties) {
        return new PersonalInformationWebMapper(
                mock(LoggingSanitizer.class),
                properties,
                new YamlResponseMapper(new ObjectMapper()));
    }

    private JsonNode toJson(FormPageResponse<FormSchemaResponse> response) {
        try {
            return objectMapper.readTree(objectMapper.writeValueAsString(response));
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Failed to serialize form response", ex);
        }
    }

    private JsonNode findSection(JsonNode json, String sectionId) {
        for (JsonNode section : json.path("form").path("sections")) {
            if (sectionId.equals(section.path("id").asText())) {
                return section;
            }
        }
        throw new AssertionError("Section not found: " + sectionId);
    }

    private JsonNode findField(JsonNode section, String fieldName) {
        for (JsonNode field : section.path("fields")) {
            if (fieldName.equals(field.path("name").asText())) {
                return field;
            }
        }
        throw new AssertionError("Field not found: " + fieldName);
    }

    @Configuration
    @EnableConfigurationProperties(BffPagesProperties.class)
    static class TestConfig {
    }
}

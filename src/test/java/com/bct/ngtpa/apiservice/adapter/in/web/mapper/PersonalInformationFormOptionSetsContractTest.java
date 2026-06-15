package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ApimBindingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FormMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.OptionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.OptionSetProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.SectionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormPageResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormSchemaResponse;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationResult;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalInformationFormOptionSetsContractTest {

    @Test
    void exposesSmsLanguagesAsFormOptionSetForSmsLanguageField() {
        PersonalInformationWebMapper mapper = new PersonalInformationWebMapper(
                mock(LoggingSanitizer.class), properties(false), new YamlResponseMapper(new ObjectMapper()));

        FormPageResponse<FormSchemaResponse> response = mapper.toFormPageResponse(
                new PersonalInformationResult(
                        Map.of("sms-language", "en"),
                        Map.of("sms-language", "EDITABLE")),
                "en",
                "DB",
                "TRUST",
                "MPF");

        assertThat(response.form().sections()).hasSize(1);
        assertThat(response.form().sections().getFirst().fields()).hasSize(1);
        assertThat(response.form().sections().getFirst().fields().getFirst().name()).isEqualTo("smsLanguage");
        assertThat(response.form().sections().getFirst().fields().getFirst().optionSource()).isEqualTo("smsLanguages");

        assertThat(response.form().optionSets()).containsOnlyKeys("smsLanguages");
        assertThat(response.form().optionSets().get("smsLanguages"))
                .extracting(option -> option.value() + ":" + option.text())
                .containsExactly("en:English", "zh_HK:Traditional Chinese");
    }

    @Test
    void resolvesOptionSetOptionsUsingConfigVariantCandidateOrderBeforeBaseCode() {
        PersonalInformationWebMapper mapper = new PersonalInformationWebMapper(
                mock(LoggingSanitizer.class), properties(true), new YamlResponseMapper(new ObjectMapper()));

        FormPageResponse<FormSchemaResponse> response = mapper.toFormPageResponse(
                new PersonalInformationResult(
                        Map.of("sms-language", "zh_HK"),
                        Map.of("sms-language", "EDITABLE")),
                "zh_HK",
                "DB",
                "TRUST",
                "MPF");

        assertThat(response.form().optionSets()).containsOnlyKeys("smsLanguages");
        assertThat(response.form().optionSets().get("smsLanguages"))
                .extracting(option -> option.value() + ":" + option.text())
                .containsExactly("en:DB英文", "zh_HK:DB繁體中文");
    }

    @Test
    void omitsOptionSetWhenNoCandidateOptionListCanBeResolved() {
        PersonalInformationWebMapper mapper = new PersonalInformationWebMapper(
                mock(LoggingSanitizer.class), propertiesWithUnresolvableOptionSet(), new YamlResponseMapper(new ObjectMapper()));

        FormPageResponse<FormSchemaResponse> response = mapper.toFormPageResponse(
                new PersonalInformationResult(
                        Map.of("sms-language", "en"),
                        Map.of("sms-language", "EDITABLE")),
                "en",
                "DB",
                "TRUST",
                "MPF");

        assertThat(response.form().optionSets()).isEmpty();
    }

    private BffPagesProperties properties(boolean withVariantOverride) {
        return properties(optionSet(withVariantOverride));
    }

    private BffPagesProperties propertiesWithUnresolvableOptionSet() {
        OptionSetProperties smsLanguages = new OptionSetProperties();
        smsLanguages.setCode("smsLanguages");
        smsLanguages.setOptions(Map.of("unexpectedCode", List.of(option("en", "English", "英文"))));
        return properties(smsLanguages);
    }

    private BffPagesProperties properties(OptionSetProperties smsLanguages) {
        FieldProperties smsLanguage = new FieldProperties();
        smsLanguage.setId("smsLanguage");
        smsLanguage.setDataType("string");
        smsLanguage.setControlType("radio");
        smsLanguage.setOptionSource("smsLanguages");
        ApimBindingProperties binding = new ApimBindingProperties();
        binding.setData("sms-language");
        binding.setConfig("sms-language");
        smsLanguage.setApimBinding(binding);

        SectionProperties section = new SectionProperties();
        section.setId("communicationSettings");
        section.setFields(List.of(smsLanguage));

        FormMetadataProperties form = new FormMetadataProperties();
        form.setId("personalInformationForm");
        form.setDefaultMode("view");
        form.setOptionSets(Map.of("smsLanguages", smsLanguages));
        form.setSections(List.of(section));

        PageMetadataProperties metadata = new PageMetadataProperties();
        metadata.setId("personalInformationPage");
        metadata.setVersion("1.0");

        PageSchemaProperties page = new PageSchemaProperties();
        page.setMetadata(metadata);
        page.setForm(form);

        BffPagesProperties properties = new BffPagesProperties();
        properties.setPages(Map.of("personalInformation", page));
        return properties;
    }

    private OptionSetProperties optionSet(boolean withVariantOverride) {
        OptionProperties english = option("en", "English", "英文");
        OptionProperties chinese = option("zh_HK", "Traditional Chinese", "繁體中文");
        OptionSetProperties smsLanguages = new OptionSetProperties();
        smsLanguages.setCode("smsLanguages");
        if (withVariantOverride) {
            OptionProperties dbEnglish = option("en", "DB English", "DB英文");
            OptionProperties dbChinese = option("zh_HK", "DB Traditional Chinese", "DB繁體中文");
            smsLanguages.setOptions(Map.of(
                    "smsLanguages.DB.TRUST.MPF", List.of(dbEnglish, dbChinese),
                    "smsLanguages", List.of(english, chinese)));
        } else {
            smsLanguages.setOptions(Map.of("smsLanguages", List.of(english, chinese)));
        }
        return smsLanguages;
    }

    private OptionProperties option(String value, String en, String zhHk) {
        OptionProperties option = new OptionProperties();
        option.setValue(value);
        option.setLabel(Map.of("en", en, "zh_HK", zhHk));
        return option;
    }
}

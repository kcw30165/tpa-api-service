package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ApimBindingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FormMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.SectionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormPageResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.FormSchemaResponse;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationResult;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalInformationWebMapperTypedResponseTest {

    @Test
    void mapsPersonalInformationToTypedFormPageResponse() {
        PersonalInformationWebMapper mapper = new PersonalInformationWebMapper(
                mock(LoggingSanitizer.class), properties(), new YamlResponseMapper(new ObjectMapper()));

        FormPageResponse<FormSchemaResponse> response = mapper.toFormPageResponse(
                new PersonalInformationResult(Map.of("email", "nick@example.com"), Map.of("email", "EDITABLE_COM")),
                "en");

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo(ApiStatus.SUCCESS);
        assertThat(response.page().id()).isEqualTo("personalInformationPage");
        assertThat(response.form().id()).isEqualTo("personalInformationForm");
        assertThat(response.form().sections()).hasSize(1);
        assertThat(response.form().sections().get(0).fields()).hasSize(1);
        assertThat(response.form().sections().get(0).fields().get(0).name()).isEqualTo("emailAddress");
        assertThat(response.form().sections().get(0).fields().get(0).value()).isEqualTo("nick@example.com");
        assertThat(response.form().sections().get(0).fields().get(0).required()).isTrue();
    }

    private BffPagesProperties properties() {
        FieldProperties field = new FieldProperties();
        field.setId("emailAddress");
        field.setLabel(Map.of("en", "Email Address"));
        field.setDataType("string");
        field.setControlType("email");
        ApimBindingProperties binding = new ApimBindingProperties();
        binding.setData("email");
        binding.setConfig("email");
        field.setApimBinding(binding);

        SectionProperties section = new SectionProperties();
        section.setId("electronicContacts");
        section.setTitle(Map.of("en", "Email Contact"));
        section.setFields(List.of(field));

        FormMetadataProperties form = new FormMetadataProperties();
        form.setId("personalInformationForm");
        form.setDefaultMode("view");
        form.setSections(List.of(section));

        PageMetadataProperties metadata = new PageMetadataProperties();
        metadata.setId("personalInformationPage");
        metadata.setVersion("1.0");
        metadata.setTitle(Map.of("en", "Personal Information"));

        PageSchemaProperties page = new PageSchemaProperties();
        page.setMetadata(metadata);
        page.setForm(form);

        BffPagesProperties properties = new BffPagesProperties();
        properties.setPages(Map.of("personalInformation", page));
        return properties;
    }
}

package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ApimBindingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FormMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.SectionProperties;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoConfigItem;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoConfigItemType;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** GET-side access rules: D-25a (requiredOverride) and D-32 (unrecognised value is HIDDEN). */
class PersonalInformationAccessRulesMapperTest {

  @Test
  void unrecognisedAccessValueOmitsFieldAndLogsErrorOnLegacyConfigPath() {
    LoggingSanitizer sanitizer = mock(LoggingSanitizer.class);

    Map<String, Object> response =
        mapper(sanitizer, page(field("target", "target-data", "target-config", null)))
            .toResponse(
                Map.of("target-data", "value"), Map.of("target-config", "SOMETHING_ELSE"), "en");

    assertThat(sections(response)).isEmpty();
    assertLoggedUnrecognised(sanitizer, "target-config", "SOMETHING_ELSE");
  }

  @Test
  void unrecognisedAccessValueOmitsFieldAndLogsErrorOnTypedConfigItemPath() {
    LoggingSanitizer sanitizer = mock(LoggingSanitizer.class);

    Map<String, Object> response =
        mapper(sanitizer, page(field("target", "target-data", "target-config", null)))
            .toResponse(
                Map.of("target-data", "value"),
                Map.of(),
                Map.of(
                    "target-config",
                    MemberInfoConfigItem.of(
                        "target-config", MemberInfoConfigItemType.DATA, "SOMETHING_ELSE")),
                "en");

    assertThat(sections(response)).isEmpty();
    assertLoggedUnrecognised(sanitizer, "target-config", "SOMETHING_ELSE");
  }

  @Test
  void recognisedAccessValuesRenderTheFieldAndLogNothing() {
    for (String value : List.of("HIDDEN", "READONLY", "EDITABLE_COM", "EDITABLE_OPTION")) {
      LoggingSanitizer sanitizer = mock(LoggingSanitizer.class);

      Map<String, Object> response =
          mapper(sanitizer, page(field("target", "target-data", "target-config", null)))
              .toResponse(Map.of("target-data", "v"), Map.of("target-config", value), "en");

      assertThat(sections(response)).hasSize("HIDDEN".equals(value) ? 0 : 1);
      verify(sanitizer, never()).toSafeString(any());
    }
  }

  @Test
  void requiredOverrideFalseSuppressesRequiredFlagOnLegacyConfigPath() {
    Map<String, Object> response =
        mapper(
                mock(LoggingSanitizer.class),
                page(
                    field("lineOne", "addr1", "addr1", Boolean.FALSE),
                    field("lineTwo", "addr2", "addr2", null)))
            .toResponse(
                Map.of("addr1", "1 Road", "addr2", "Tai Po"),
                Map.of("addr1", "EDITABLE_COM", "addr2", "EDITABLE_COM"),
                "en");

    assertThat(fieldByName(response, "lineOne"))
        .containsEntry("required", false)
        .containsEntry("readonly", false);
    assertThat(fieldByName(response, "lineTwo")).containsEntry("required", true);
  }

  @Test
  void requiredOverrideFalseSuppressesRequiredFlagOnTypedConfigItemPath() {
    Map<String, Object> response =
        mapper(
                mock(LoggingSanitizer.class),
                page(
                    field("lineOne", "addr1", "addr1", Boolean.FALSE),
                    field("lineTwo", "addr2", "addr2", null)))
            .toResponse(
                Map.of("addr1", "1 Road", "addr2", "Tai Po"),
                Map.of(),
                Map.of(
                    "addr1",
                    MemberInfoConfigItem.of("addr1", MemberInfoConfigItemType.DATA, "EDITABLE_COM"),
                    "addr2",
                    MemberInfoConfigItem.of(
                        "addr2", MemberInfoConfigItemType.DATA, "EDITABLE_COM")),
                "en");

    assertThat(fieldByName(response, "lineOne")).containsEntry("required", false);
    assertThat(fieldByName(response, "lineTwo")).containsEntry("required", true);
  }

  @Test
  void requiredOverrideDoesNotChangeReadonly() {
    Map<String, Object> response =
        mapper(
                mock(LoggingSanitizer.class),
                page(field("lineOne", "addr1", "addr1", Boolean.FALSE)))
            .toResponse(Map.of("addr1", "1 Road"), Map.of("addr1", "READONLY"), "en");

    assertThat(fieldByName(response, "lineOne"))
        .containsEntry("readonly", true)
        .containsEntry("required", false);
  }

  @Test
  void nullOrBlankAccessValueOmitsFieldAndLogsErrorOnLegacyConfigPath() {
    for (String value : new String[] {null, "", "   "}) {
      LoggingSanitizer sanitizer = mock(LoggingSanitizer.class);
      Map<String, String> legacyConfig = new LinkedHashMap<>();
      legacyConfig.put("target-config", value);

      Map<String, Object> response =
          mapper(sanitizer, page(field("target", "target-data", "target-config", null)))
              .toResponse(Map.of("target-data", "value"), legacyConfig, "en");

      assertThat(sections(response)).isEmpty();
      assertLoggedUnrecognised(sanitizer, "target-config", value);
    }
  }

  @Test
  void blankAccessValueOmitsFieldAndLogsErrorOnTypedConfigItemPath() {
    for (String value : new String[] {"", "   "}) {
      LoggingSanitizer sanitizer = mock(LoggingSanitizer.class);

      Map<String, Object> response =
          mapper(sanitizer, page(field("target", "target-data", "target-config", null)))
              .toResponse(
                  Map.of("target-data", "value"),
                  Map.of(),
                  Map.of(
                      "target-config",
                      MemberInfoConfigItem.of(
                          "target-config", MemberInfoConfigItemType.DATA, value)),
                  "en");

      assertThat(sections(response)).isEmpty();
      assertLoggedItem(sanitizer, "target-config");
    }
  }

  @Test
  void blankAccessValueIsLoggedOnceAndNotAlsoReportedAsMissingExpectedItem() {
    LoggingSanitizer sanitizer = mock(LoggingSanitizer.class);
    Map<String, String> legacyConfig = new LinkedHashMap<>();
    legacyConfig.put("target-config", "");

    mapper(sanitizer, page(field("target", "target-data", "target-config", null)))
        .toResponse(Map.of("target-data", "value"), legacyConfig, "en");

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(sanitizer, atLeastOnce()).toSafeString(captor.capture());
    assertThat(captor.getAllValues())
        .hasSize(1)
        .noneMatch(event -> ((Map<?, ?>) event).containsKey("expected-item-id"));
  }

  private static void assertLoggedItem(LoggingSanitizer sanitizer, String itemId) {
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(sanitizer, atLeastOnce()).toSafeString(captor.capture());
    assertThat(captor.getAllValues())
        .anySatisfy(
            event -> {
              @SuppressWarnings("unchecked")
              Map<String, Object> map = (Map<String, Object>) event;
              assertThat(map).containsEntry("item-id", itemId).containsKey("config-value");
            });
  }

  private static void assertLoggedUnrecognised(
      LoggingSanitizer sanitizer, String itemId, String value) {
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(sanitizer, atLeastOnce()).toSafeString(captor.capture());
    assertThat(captor.getAllValues())
        .anySatisfy(
            event -> {
              @SuppressWarnings("unchecked")
              Map<String, Object> map = (Map<String, Object>) event;
              assertThat(map).containsEntry("item-id", itemId).containsEntry("config-value", value);
            });
  }

  private static PersonalInformationWebMapper mapper(
      LoggingSanitizer sanitizer, PageSchemaProperties page) {
    BffPagesProperties properties = new BffPagesProperties();
    properties.setPages(Map.of("personalInformation", page));
    return new PersonalInformationWebMapper(sanitizer, properties);
  }

  private static PageSchemaProperties page(FieldProperties... fields) {
    PageMetadataProperties metadata = new PageMetadataProperties();
    metadata.setId("personalInformationPage");
    metadata.setVersion("1.0");
    SectionProperties section = new SectionProperties();
    section.setId("main");
    section.setTitle(Map.of("en", "Main"));
    section.setFields(List.of(fields));
    FormMetadataProperties form = new FormMetadataProperties();
    form.setId("personalInformationForm");
    form.setSections(List.of(section));
    PageSchemaProperties page = new PageSchemaProperties();
    page.setMetadata(metadata);
    page.setForm(form);
    return page;
  }

  private static FieldProperties field(
      String id, String dataKey, String configKey, Boolean requiredOverride) {
    ApimBindingProperties binding = new ApimBindingProperties();
    binding.setData(dataKey);
    binding.setConfig(configKey);
    FieldProperties field = new FieldProperties();
    field.setId(id);
    field.setLabel(Map.of("en", id));
    field.setDataType("string");
    field.setControlType("text");
    field.setApimBinding(binding);
    field.setRequiredOverride(requiredOverride);
    return field;
  }

  @SuppressWarnings("unchecked")
  private static List<?> sections(Map<String, Object> response) {
    return (List<?>) ((Map<String, Object>) response.get("form")).get("sections");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> fieldByName(Map<String, Object> response, String name) {
    for (Object section : sections(response)) {
      for (Object field : (List<?>) ((Map<String, Object>) section).get("fields")) {
        Map<String, Object> fieldMap = (Map<String, Object>) field;
        if (name.equals(fieldMap.get("name"))) {
          return fieldMap;
        }
      }
    }
    throw new AssertionError("Field not rendered: " + name);
  }
}

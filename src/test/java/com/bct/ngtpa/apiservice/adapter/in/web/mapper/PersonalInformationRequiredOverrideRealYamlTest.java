package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PersonalInformationYamlContextRunner;
import com.bct.ngtpa.apiservice.infrastructure.logging.LoggingSanitizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Binds the REAL application-page-personal-information.yml and runs the real GET mapper (D-25a):
 * the downstream config marks both line-1 fields EDITABLE_COM, but the GET must send required=false
 * for them; every other EDITABLE_COM field stays required.
 */
class PersonalInformationRequiredOverrideRealYamlTest {

  @Test
  void editableComAddressLineOneIsNotRequiredInGetResponseButOtherFieldsAre() {
    PersonalInformationYamlContextRunner.create()
        .run(
            context -> {
              BffPagesProperties properties = context.getBean(BffPagesProperties.class);
              PersonalInformationWebMapper mapper =
                  new PersonalInformationWebMapper(mock(LoggingSanitizer.class), properties);
              Map<String, String> config = new LinkedHashMap<>();
              for (String id : List.of("addr1", "addr2", "corr-addr1", "corr-addr2", "email")) {
                config.put(id, "EDITABLE_COM");
              }

              Map<String, Object> response = mapper.toResponse(Map.of(), config, "en");

              assertThat(fieldByName(response, "residentialAddressLine1"))
                  .containsEntry("required", false);
              assertThat(fieldByName(response, "mailingAddressLine1"))
                  .containsEntry("required", false);
              assertThat(fieldByName(response, "residentialAddressLine2"))
                  .containsEntry("required", true);
              assertThat(fieldByName(response, "mailingAddressLine2"))
                  .containsEntry("required", true);
              assertThat(fieldByName(response, "emailAddress")).containsEntry("required", true);
            });
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> fieldByName(Map<String, Object> response, String name) {
    Map<String, Object> form = (Map<String, Object>) response.get("form");
    for (Object section : (List<?>) form.get("sections")) {
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

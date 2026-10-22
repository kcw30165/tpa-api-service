package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** D-25a: only the two address line-1 fields carry requiredOverride in the real YAML. */
class PersonalInformationRequiredOverrideYamlBindingTest {

  @Test
  void onlyAddressLineOneFieldsCarryRequiredOverrideFalse() {
    PersonalInformationYamlContextRunner.create()
        .run(
            context -> {
              BffPagesProperties properties = context.getBean(BffPagesProperties.class);
              PageSchemaProperties page = properties.getPages().get("personalInformation");
              Map<String, Boolean> overrides = new LinkedHashMap<>();
              for (SectionProperties section : page.getForm().getSections()) {
                for (FieldProperties field : section.getFields()) {
                  if (field.getRequiredOverride() != null) {
                    overrides.put(field.getId(), field.getRequiredOverride());
                  }
                }
              }

              assertThat(overrides)
                  .containsOnly(
                      entry("residentialAddressLine1", false), entry("mailingAddressLine1", false));
            });
  }
}

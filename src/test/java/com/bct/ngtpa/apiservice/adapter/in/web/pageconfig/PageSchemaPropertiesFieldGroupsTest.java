package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageSchemaPropertiesFieldGroupsTest {

  @Test
  void fieldGroupsAreNeverNull() {
    PageSchemaProperties page = new PageSchemaProperties();
    assertThat(page.getFieldGroups()).isEmpty();

    page.setFieldGroups(null);
    assertThat(page.getFieldGroups()).isEmpty();
  }

  @Test
  void settingFieldGroupsKeepsTheGivenGroups() {
    FieldGroupProperties group = new FieldGroupProperties();
    group.setName("address");
    group.setFields(List.of("line1", "country"));
    PageSchemaProperties page = new PageSchemaProperties();

    page.setFieldGroups(List.of(group));

    assertThat(page.getFieldGroups()).containsExactly(group);
    assertThat(group.getName()).isEqualTo("address");
    assertThat(group.getFields()).containsExactly("line1", "country");
  }

  @Test
  void fieldGroupFieldsAreNeverNull() {
    FieldGroupProperties group = new FieldGroupProperties();
    assertThat(group.getFields()).isEmpty();

    group.setFields(null);
    assertThat(group.getFields()).isEmpty();
  }
}

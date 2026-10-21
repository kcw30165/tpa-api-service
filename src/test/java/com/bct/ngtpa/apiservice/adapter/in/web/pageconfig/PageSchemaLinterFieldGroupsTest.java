package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PageSchemaLinterFieldGroupsTest {

  private static final String PAGE_KEY = "p";
  private static final String LINE_1 = "line1";
  private static final String LINE_2 = "line2";
  private static final String COUNTRY = "country";
  private static final String PHONE = "phone";
  private static final String ADDRESS = "address";
  private static final String OTHER = "other";

  @Test
  void acceptsFieldGroupsThatReferenceKnownFieldsOnce() {
    List<FieldGroupProperties> groups =
        List.of(group(ADDRESS, LINE_1, LINE_2, COUNTRY), group("contact", PHONE));

    assertThat(PageSchemaLinter.lint(propertiesWith(groups)).getErrors()).isEmpty();
  }

  @Test
  void pagesWithoutFieldGroupsProduceNoFieldGroupErrors() {
    assertThat(PageSchemaLinter.lint(propertiesWith(List.of())).getErrors()).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidFieldGroups")
  void reportsInvalidFieldGroups(
      String caseName, List<FieldGroupProperties> groups, String expectedError) {
    List<String> errors = PageSchemaLinter.lint(propertiesWith(groups)).getErrors();

    assertThat(errors).as(caseName).anyMatch(error -> error.contains(expectedError));
  }

  static Stream<Arguments> invalidFieldGroups() {
    List<FieldGroupProperties> withNullGroup = new ArrayList<>();
    withNullGroup.add(null);
    return Stream.of(
        Arguments.of("null group", withNullGroup, "p: field group is null"),
        Arguments.of("blank name", List.of(group(" ", LINE_1)), "p: field group has blank name"),
        Arguments.of(
            "duplicate name",
            List.of(group(ADDRESS, LINE_1), group(ADDRESS, LINE_2)),
            "field group address is defined more than once"),
        Arguments.of("no fields", List.of(group(ADDRESS)), "field group address has no fields"),
        Arguments.of(
            "blank field",
            List.of(group(ADDRESS, " ")),
            "field group address contains a blank field"),
        Arguments.of(
            "unknown field",
            List.of(group(ADDRESS, "missingField")),
            "field group address references unknown field missingField"),
        Arguments.of(
            "field in two groups",
            List.of(group(ADDRESS, LINE_1), group(OTHER, LINE_1)),
            "field line1 belongs to more than one field group (address, other)"),
        Arguments.of(
            "field twice in one group",
            List.of(group(ADDRESS, LINE_1, LINE_1)),
            "field line1 is listed more than once in field group address"));
  }

  private static FieldGroupProperties group(String name, String... fields) {
    FieldGroupProperties group = new FieldGroupProperties();
    group.setName(name);
    group.setFields(new ArrayList<>(Arrays.asList(fields)));
    return group;
  }

  private static FieldProperties field(String id) {
    FieldProperties field = new FieldProperties();
    field.setId(id);
    return field;
  }

  private static BffPagesProperties propertiesWith(List<FieldGroupProperties> groups) {
    SectionProperties section = new SectionProperties();
    section.setId("s1");
    section.setFields(
        Stream.of(LINE_1, LINE_2, COUNTRY, PHONE, OTHER)
            .map(PageSchemaLinterFieldGroupsTest::field)
            .toList());
    FormMetadataProperties form = new FormMetadataProperties();
    form.setSections(List.of(section));
    PageSchemaProperties page = new PageSchemaProperties();
    page.setForm(form);
    page.setFieldGroups(groups);
    BffPagesProperties properties = new BffPagesProperties();
    properties.setPages(Map.of(PAGE_KEY, page));
    return properties;
  }
}

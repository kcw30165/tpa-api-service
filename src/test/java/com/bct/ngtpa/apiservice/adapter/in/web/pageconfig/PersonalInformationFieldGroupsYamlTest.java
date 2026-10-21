package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.yaml.snakeyaml.Yaml;

class PersonalInformationFieldGroupsYamlTest {

  private static final String YAML_RESOURCE = PersonalInformationYamlContextRunner.YAML_RESOURCE;
  private static final String PAGE_KEY = "personalInformation";
  private static final String RESIDENTIAL = "residentialAddress";
  private static final String MAILING = "mailingAddress";
  private static final String BUSINESS_PHONE = "businessPhone";
  private static final String OVERSEAS_PHONE = "overseasPhone";
  private static final String OVERSEAS_EXTENSION = "overseasPhoneExtension";

  private final ApplicationContextRunner contextRunner =
      PersonalInformationYamlContextRunner.create();

  @Test
  void bindsTheFourFieldGroupsInDeclaredOrder() {
    contextRunner.run(
        context -> {
          Map<String, List<String>> groups = boundGroups(context.getBean(BffPagesProperties.class));

          assertThat(groups.keySet())
              .containsExactly(RESIDENTIAL, MAILING, BUSINESS_PHONE, OVERSEAS_PHONE);
          assertThat(groups.get(RESIDENTIAL))
              .containsExactly(
                  "residentialAddressLine1",
                  "residentialAddressLine2",
                  "residentialAddressLine3",
                  "residentialCountry");
          assertThat(groups.get(MAILING))
              .containsExactly(
                  "mailingAddressLine1",
                  "mailingAddressLine2",
                  "mailingAddressLine3",
                  "mailingCountry");
          assertThat(groups.get(BUSINESS_PHONE))
              .containsExactly("hongKongBusinessPhone", "hongKongBusinessPhoneExtension");
          assertThat(groups.get(OVERSEAS_PHONE))
              .containsExactly("overseasCountryCode", "overseasAreaCode", "overseasPhoneNumber");
        });
  }

  @Test
  void overseasPhoneExtensionStaysStandaloneUntilTheStakeholderConfirms() {
    contextRunner.run(
        context -> {
          BffPagesProperties properties = context.getBean(BffPagesProperties.class);
          List<String> groupedFields =
              boundGroups(properties).values().stream().flatMap(List::stream).toList();
          List<String> formFields =
              properties.getPages().get(PAGE_KEY).getForm().getSections().stream()
                  .flatMap(section -> section.getFields().stream())
                  .map(FieldProperties::getId)
                  .toList();

          assertThat(groupedFields).isNotEmpty().doesNotContain(OVERSEAS_EXTENSION);
          assertThat(formFields).contains(OVERSEAS_EXTENSION);
        });
  }

  @Test
  void realYamlPassesThePageSchemaLinterWithFieldGroups() {
    contextRunner.run(
        context -> {
          BffPagesProperties properties = context.getBean(BffPagesProperties.class);

          assertThat(properties.getPages().get(PAGE_KEY).getFieldGroups()).hasSize(4);
          assertThat(PageSchemaLinter.lint(properties).getErrors()).isEmpty();
        });
  }

  @Test
  void addressFieldGroupsMatchTheGroupsOfTheAtLeastOneRequiredRule() {
    Map<String, List<String>> ruleGroups = new LinkedHashMap<>();
    for (Map<String, Object> group : atLeastOneRequiredRuleGroups()) {
      ruleGroups.put(String.valueOf(group.get("name")), strings(group.get("fields")));
    }

    contextRunner.run(
        context -> {
          Map<String, List<String>> bound = boundGroups(context.getBean(BffPagesProperties.class));

          assertThat(ruleGroups.keySet()).containsExactly(RESIDENTIAL, MAILING);
          assertThat(bound).containsAllEntriesOf(ruleGroups);
        });
  }

  private static Map<String, List<String>> boundGroups(BffPagesProperties properties) {
    Map<String, List<String>> groups = new LinkedHashMap<>();
    for (FieldGroupProperties group : properties.getPages().get(PAGE_KEY).getFieldGroups()) {
      groups.put(group.getName(), group.getFields());
    }
    return groups;
  }

  private static List<String> strings(Object value) {
    return ((List<?>) value).stream().map(String::valueOf).toList();
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> atLeastOneRequiredRuleGroups() {
    try (InputStream input =
        PersonalInformationFieldGroupsYamlTest.class
            .getClassLoader()
            .getResourceAsStream(YAML_RESOURCE)) {
      assertThat(input).as(YAML_RESOURCE + " must be on the test classpath").isNotNull();
      Map<String, Object> root = new Yaml().load(input);
      Map<String, Object> pages =
          (Map<String, Object>) ((Map<String, Object>) root.get("bff-pages")).get("pages");
      Map<String, Object> page = (Map<String, Object>) pages.get(PAGE_KEY);
      for (Map<String, Object> rule : (List<Map<String, Object>>) page.get("validations")) {
        if ("address.atLeastOneRequired".equals(rule.get("id"))) {
          Map<String, Object> when = (Map<String, Object>) rule.get("when");
          return (List<Map<String, Object>>) when.get("groups");
        }
      }
      throw new AssertionError("rule address.atLeastOneRequired not found in " + YAML_RESOURCE);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}

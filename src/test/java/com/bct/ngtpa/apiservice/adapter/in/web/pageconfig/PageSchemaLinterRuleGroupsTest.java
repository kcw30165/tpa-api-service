package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PageSchemaLinterRuleGroupsTest {

  private static final String PAGE_KEY = "p";
  private static final String KNOWN = "line1";
  private static final String UNKNOWN = "missing";

  @Test
  void flagsAnUnknownFieldInAnIndexKeyedRuleGroup() {
    Map<String, Object> indexed = new LinkedHashMap<>();
    indexed.put("0", KNOWN);
    indexed.put("1", UNKNOWN);

    assertThat(lintErrors(indexed))
        .anyMatch(error -> error.contains("references unknown field " + UNKNOWN));
  }

  @Test
  void flagsAnUnknownFieldInAListRuleGroup() {
    assertThat(lintErrors(List.of(KNOWN, UNKNOWN)))
        .anyMatch(error -> error.contains("references unknown field " + UNKNOWN));
  }

  @Test
  void acceptsKnownFieldsInAnIndexKeyedRuleGroup() {
    Map<String, Object> indexed = new LinkedHashMap<>();
    indexed.put("0", KNOWN);

    assertThat(lintErrors(indexed)).isEmpty();
  }

  @Test
  void ignoresRuleGroupFieldsOfAnUnsupportedShape() {
    assertThat(lintErrors(UNKNOWN)).isEmpty();
  }

  private static List<String> lintErrors(Object groupFields) {
    FieldProperties field = new FieldProperties();
    field.setId(KNOWN);
    SectionProperties section = new SectionProperties();
    section.setId("s1");
    section.setFields(List.of(field));
    FormMetadataProperties form = new FormMetadataProperties();
    form.setSections(List.of(section));

    RuleConditionProperties when = new RuleConditionProperties();
    when.setOperator("allGroupsEmpty");
    when.setGroups(List.of(Map.of("name", "g", "fields", groupFields)));
    RuleActionProperties then = new RuleActionProperties();
    then.setOperator("fail");
    then.setTargets(List.of(KNOWN));
    ValidationRuleProperties rule = new ValidationRuleProperties();
    rule.setId("r1");
    rule.setType("groupRequirement");
    rule.setWhen(when);
    rule.setThen(then);

    PageSchemaProperties page = new PageSchemaProperties();
    page.setForm(form);
    page.setValidations(List.of(rule));
    BffPagesProperties properties = new BffPagesProperties();
    properties.setPages(Map.of(PAGE_KEY, page));
    return PageSchemaLinter.lint(properties).getErrors();
  }
}

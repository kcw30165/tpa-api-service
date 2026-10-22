package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Binds the real page YAML through Spring Boot and reads the address rule groups the way the linter
 * and the validator do. Spring may hand the nested field lists over as index-keyed maps.
 */
class PersonalInformationRuleGroupsBindingTest {

  private static final String RULE_ID = "address.atLeastOneRequired";

  @Test
  void addressRuleGroupsAreReadableFromTheRealBoundYaml() {
    PersonalInformationYamlContextRunner.create()
        .run(
            context -> {
              PageSchemaProperties page =
                  context.getBean(BffPagesProperties.class).getPages().get("personalInformation");
              ValidationRuleProperties rule =
                  page.getValidations().stream()
                      .filter(candidate -> RULE_ID.equals(candidate.getId()))
                      .findFirst()
                      .orElseThrow();
              List<Map<String, Object>> groups = rule.getWhen().getGroups();

              assertThat(groups).hasSize(2);
              assertThat(RuleGroupFields.of(groups.get(0).get("fields")))
                  .containsExactly("residentialAddressLine1");
              assertThat(RuleGroupFields.of(groups.get(1).get("fields")))
                  .containsExactly("mailingAddressLine1");
            });
  }
}

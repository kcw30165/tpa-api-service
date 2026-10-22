package com.bct.ngtpa.apiservice.adapter.in.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PageDisplayTextResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PersonalInformationYamlContextRunner;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Binds the REAL application-page-personal-information.yml through Spring Boot (the same harness
 * M2.1's PersonalInformationRuleGroupsBindingTest uses) and runs the real validator end to end.
 *
 * <p>Probe 011 (HEAD 081b2d9e) showed address.atLeastOneRequired's group fields bind as
 * LinkedHashMap rather than List, so the pre-M2.3 validator (instanceof List check in groupIsEmpty)
 * never evaluated them against real configuration - only against hand-built Java List fixtures in
 * other tests. This test fails before the M2.3 fix and passes after.
 */
class PersonalInformationAddressGroupRealYamlRegressionTest {

  @Test
  void addressAtLeastOneRequiredFiresAgainstTheRealBoundYamlWhenBothGroupsAreBlank() {
    PersonalInformationYamlContextRunner.create()
        .run(
            context -> {
              BffPagesProperties properties = context.getBean(BffPagesProperties.class);
              PersonalInformationUpdateYamlValidator validator =
                  new PersonalInformationUpdateYamlValidator(
                      properties,
                      new PageDisplayTextResolver(new ConfigVariantCandidateGenerator()),
                      new ConfigVariantCandidateGenerator());
              Map<String, Object> fields = new LinkedHashMap<>();
              for (String f :
                  List.of(
                      "residentialAddressLine1",
                      "residentialAddressLine2",
                      "residentialAddressLine3",
                      "residentialCountry",
                      "mailingAddressLine1",
                      "mailingAddressLine2",
                      "mailingAddressLine3",
                      "mailingCountry")) {
                fields.put(f, "");
              }
              fields.put("emailAddress", "member@example.test");

              List<ApiError> errors =
                  validator.validate(
                      new UpdatePersonalInformationRequest("1.0", false, fields),
                      "en",
                      "JP",
                      "JPM",
                      "OE");

              assertThat(errors)
                  .anyMatch(
                      error ->
                          "personalInformation.address.atLeastOneRequired".equals(error.code()));
            });
  }
}

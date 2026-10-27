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
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Binds the REAL application-page-personal-information.yml through Spring Boot and runs the real
 * validator end to end (D-26, D-28, D-29: line 1 defines an address, the at-least-one rule is
 * evaluated only when both line-1 keys are submitted blank, and country-depends-on-address rules
 * are not applied).
 */
class PersonalInformationAddressGroupRealYamlRegressionTest {

  private static final String AT_LEAST_ONE = "personalInformation.address.atLeastOneRequired";
  private static final String EMAIL = "member@example.test";

  @Test
  void addressAtLeastOneRequiredFiresAgainstTheRealBoundYamlWhenBothGroupsAreBlank() {
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
    fields.put("emailAddress", EMAIL);

    validateWithRealYaml(
        fields, errors -> assertThat(errors).anyMatch(error -> AT_LEAST_ONE.equals(error.code())));
  }

  @Test
  void bothLineOneKeysSubmittedBlankFiresAddressRequirement() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("residentialAddressLine1", "");
    fields.put("mailingAddressLine1", "");
    fields.put("emailAddress", EMAIL);

    validateWithRealYaml(
        fields, errors -> assertThat(errors).anyMatch(error -> AT_LEAST_ONE.equals(error.code())));
  }

  @Test
  void onlyEmailSubmittedDoesNotTriggerAddressRequirement() {
    validateWithRealYaml(
        Map.of("emailAddress", EMAIL),
        errors -> assertThat(errors).noneMatch(error -> AT_LEAST_ONE.equals(error.code())));
  }

  @Test
  void lineOneWithoutCountryHasNoCountryOrIncompleteError() {
    validateWithRealYaml(
        Map.of(
            "residentialAddressLine1", "12345 Residential Road",
            "mailingAddressLine1", "12345 Mailing Road",
            "emailAddress", EMAIL),
        errors ->
            assertThat(errors)
                .noneMatch(error -> error.targets().contains("residentialCountry"))
                .noneMatch(error -> error.targets().contains("mailingCountry"))
                .noneMatch(error -> error.code().endsWith(".incomplete")));
  }

  @Test
  void lineTwoOrThreeWithoutLineOneIsAccepted() {
    validateWithRealYaml(
        Map.of(
            "residentialAddressLine2", "Tai Po",
            "mailingAddressLine3", "Kowloon",
            "emailAddress", EMAIL),
        errors -> assertThat(errors).isEmpty());
  }

  private void validateWithRealYaml(
      Map<String, Object> fields, Consumer<List<ApiError>> assertions) {
    PersonalInformationYamlContextRunner.create()
        .run(
            context -> {
              BffPagesProperties properties = context.getBean(BffPagesProperties.class);
              PersonalInformationUpdateYamlValidator validator =
                  new PersonalInformationUpdateYamlValidator(
                      properties,
                      new PageDisplayTextResolver(new ConfigVariantCandidateGenerator()),
                      new ConfigVariantCandidateGenerator());
              assertions.accept(
                  validator.validate(
                      new UpdatePersonalInformationRequest("1.0", false, fields),
                      "en",
                      "JP",
                      "JPM",
                      "OE"));
            });
  }
}

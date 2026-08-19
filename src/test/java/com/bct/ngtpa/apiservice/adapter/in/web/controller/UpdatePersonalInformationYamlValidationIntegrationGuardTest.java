package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UpdatePersonalInformationYamlValidationIntegrationGuardTest {

  private static final Path CONTROLLER =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/PersonalInformationController.java");

  @Test
  void controllerInjectsGenericYamlValidatorForPreApimValidation() throws Exception {
    String source = source();

    assertThat(source)
        .as(
            "PUT personal-information must inject the generic YAML validator before APIM/use-case execution")
        .containsIgnoringWhitespaces("PersonalInformationUpdateYamlValidator")
        .containsIgnoringWhitespaces("private final PersonalInformationUpdateYamlValidator")
        .containsIgnoringWhitespaces("validator.validate");
  }

  @Test
  void validationFailureReturnsMutationResponseErrorsWithoutCallingUseCase() throws Exception {
    String source = source();

    assertThat(source)
        .as("Blocking BFF YAML validation errors must return the mutation error contract")
        .containsIgnoringWhitespaces("ApiStatus.VALIDATION_FAILED")
        .containsIgnoringWhitespaces("MutationResponse.failure")
        .containsIgnoringWhitespaces("errors")
        .containsIgnoringWhitespaces("!errors.isEmpty()");

    assertThat(indexOf(source, "validator.validate"))
        .as("validator must run before request mapping/use-case execution")
        .isLessThan(indexOf(source, "requestMapper.toCommand"));
    assertThat(indexOf(source, "validator.validate"))
        .as("validator must run before APIM/use-case execution")
        .isLessThan(indexOf(source, "updatePersonalInformationUseCase.execute"));
    assertThat(indexOf(source, "MutationResponse.failure"))
        .as("validation failure branch must be before APIM/use-case execution")
        .isLessThan(indexOf(source, "updatePersonalInformationUseCase.execute"));
  }

  @Test
  void controllerPassesAcceptLanguageAndDoesNotUseLangQueryParameterForValidation()
      throws Exception {
    String source = source();

    assertThat(source)
        .as("validation message language must come from RequestHeaderContext / Accept-Language")
        .containsIgnoringWhitespaces("resolveLanguage(contextView, acceptLanguage)")
        .doesNotContain("@RequestParam");
  }

  private String source() throws Exception {
    assertThat(CONTROLLER).exists();
    return Files.readString(CONTROLLER);
  }

  private int indexOf(String source, String token) {
    int index = compact(source).indexOf(compact(token));
    assertThat(index)
        .as("Expected controller source to contain token: " + token)
        .isGreaterThanOrEqualTo(0);
    return index;
  }

  private String compact(String value) {
    return value.replaceAll("\\s+", "");
  }
}

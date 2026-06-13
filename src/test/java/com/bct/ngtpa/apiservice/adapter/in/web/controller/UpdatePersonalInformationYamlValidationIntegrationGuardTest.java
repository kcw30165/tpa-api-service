package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UpdatePersonalInformationYamlValidationIntegrationGuardTest {

    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/UpdatePersonalInformationController.java");

    @Test
    void controllerInjectsGenericYamlValidatorForPreApimValidation() throws Exception {
        String source = source();

        assertThat(source)
                .as("PUT personal-information must inject the generic YAML validator before APIM/use-case execution")
                .contains("PersonalInformationUpdateYamlValidator")
                .contains("private final PersonalInformationUpdateYamlValidator")
                .contains("this.validator")
                .contains("validator.validate");
    }

    @Test
    void validationFailureReturnsMutationResponseErrorsWithoutCallingUseCase() throws Exception {
        String source = source();

        assertThat(source)
                .as("Blocking BFF YAML validation errors must return the mutation error contract")
                .contains("ApiStatus.VALIDATION_FAILED")
                .contains("MutationResponse.failure")
                .contains("errors")
                .contains("!errors.isEmpty()");

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
    void controllerPassesAcceptLanguageAndDoesNotUseLangQueryParameterForValidation() throws Exception {
        String source = source();

        assertThat(source)
                .as("validation message language must come from RequestHeaderContext / Accept-Language")
                .contains("context.language()")
                .doesNotContain("@RequestParam")
                .doesNotContain("lang");
    }

    private String source() throws Exception {
        assertThat(CONTROLLER).exists();
        return Files.readString(CONTROLLER);
    }

    private int indexOf(String source, String token) {
        int index = source.indexOf(token);
        assertThat(index).as("Expected controller source to contain token: " + token).isGreaterThanOrEqualTo(0);
        return index;
    }
}

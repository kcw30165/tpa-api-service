package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PersonalInformationGetValidationResponseContractDocumentationTest {

    private static final Path CONTRACT = Path.of("docs/others/personal-information-get-validation-response-contract.md");

    @Test
    void contractDocumentationCapturesFeValidationResponseRules() throws Exception {
        assertThat(CONTRACT).exists();
        String document = Files.readString(CONTRACT);

        assertThat(document)
                .contains("GET /api/v1/personal-information")
                .contains("form.sections[].fields[].validations[]")
                .contains("form.validationRules[]")
                .contains("validationRules[].when.groups[].fields")
                .contains("messageCode")
                .contains("labelCode")
                .contains("placeholderCode")
                .contains("apimBinding")
                .contains("must not be returned")
                .contains("Numeric-indexed map to array conversion")
                .contains("Ordinary maps must stay as objects")
                .contains("PersonalInformationFeValidationResponseContractTest")
                .contains("PersonalInformationFeValidationResponseNormalizationRegressionTest")
                .contains("PersonalInformationGetValidationResponseBoundaryGuardTest");
    }
}

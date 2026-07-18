package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.mapper.CaptchaWebMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FrontendOwnedLocalizationContractFailFirstTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path MAIN_RESOURCES = Path.of("src/main/resources");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void registrationErrorsExposeStableCodeWithoutResolvedMessage() {
        var mapper = new CaptchaWebMapper((code, locale, accountEnv, trustCode, schemeType) ->
                "server-resolved-" + locale + "-message");

        JsonNode response = objectMapper.valueToTree(mapper.missingAnswerResponse("en"));
        JsonNode error = response.path("errors").path(0);

        assertThat(error.path("code").asText())
                .isEqualTo("err.registration.captcha.required");
        assertThat(error.path("targets").path(0).asText())
                .isEqualTo("captchaAnswer");
        assertThat(error.has("message"))
                .as("FE resolves Java/BFF-owned error text from errors[].code")
                .isFalse();
    }

    @Test
    void registrationOwnedErrorPayloadIsLanguageIndependent() {
        var mapper = new CaptchaWebMapper((code, locale, accountEnv, trustCode, schemeType) ->
                "server-resolved-" + locale + "-message");

        JsonNode en = objectMapper.valueToTree(mapper.missingAnswerResponse("en"));
        JsonNode zh = objectMapper.valueToTree(mapper.missingAnswerResponse("zh-HK"));

        assertThat(en)
                .as("Accept-Language must not cause Java to resolve FE-owned registration text")
                .isEqualTo(zh);
        assertThat(en.path("messages").isArray()).isTrue();
        assertThat(en.path("messages")).isEmpty();
    }

    @Test
    void registrationWebMappersDoNotResolvePresentationText() throws Exception {
        List<String> files = List.of(
                "CaptchaWebMapper.java",
                "RegistrationStartWebMapper.java",
                "RegistrationOtpResendWebMapper.java",
                "CompleteRegistrationWebMapper.java",
                "CancelRegistrationWebMapper.java",
                "RequiredRequestIdWebFilter.java");

        for (String file : files) {
            String source = readMainJava(file);
            assertThat(source)
                    .as(file + " must return stable codes instead of resolving FE-owned text")
                    .doesNotContain("ErrorMessageResolver")
                    .doesNotContain("resolveMessage(")
                    .doesNotContain("messages.resolve(")
                    .doesNotContain("errorMessageResolver.resolve(");
        }
    }

    @Test
    void registrationPagesExposeSemanticIdentifiersInsteadOfResolvedDisplayText() throws Exception {
        String cancelledMapper = readMainJava("RegistrationCancellationPageMapper.java");
        String successMapper = readMainJava("RegistrationSuccessPageMapper.java");
        String cancelledResponse = readMainJava("RegistrationCancellationPageResponse.java");
        String successResponse = readMainJava("RegistrationSuccessPageResponse.java");

        assertThat(cancelledMapper + successMapper)
                .doesNotContain("PageDisplayTextResolver")
                .doesNotContain("displayTextResolver.resolve(");
        assertThat(cancelledResponse + successResponse)
                .doesNotContain("String title")
                .doesNotContain("String body")
                .doesNotContain("String label")
                .contains("pageId");
    }

    @Test
    void registrationWorkflowErrorYamlContainsSemanticMetadataButNoTranslations() throws Exception {
        String yaml = Files.readString(MAIN_RESOURCES.resolve("application-registration-errors.yml"));

        assertThat(yaml)
                .contains("workflow-error-contract:")
                .contains("targets:")
                .contains("next-step:")
                .doesNotContain("messages:");
    }

    @Test
    void acceptLanguageRemainsForApimOwnedLocalizedContent() throws Exception {
        String contributionController = readMainJava("ContributionController.java");
        String referenceDataController = readMainJava("ReferenceDataController.java");

        assertThat(contributionController)
                .contains("ACCEPT_LANGUAGE_HEADER")
                .contains("GetContributionSummaryCommand")
                .contains("resolvedLang");
        assertThat(referenceDataController)
                .contains("ACCEPT_LANGUAGE_HEADER")
                .contains("GetReferenceDataCountriesCommand")
                .contains("language");
    }

    private String readMainJava(String fileName) throws IOException {
        try (var paths = Files.walk(MAIN_JAVA)) {
            Path path = paths
                    .filter(candidate -> candidate.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Production source not found: " + fileName));
            return Files.readString(path);
        }
    }
}

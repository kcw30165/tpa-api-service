package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.captcha.mapper.CaptchaWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiMessage;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class FrontendOwnedLocalizationMessageFreeErrorsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void registrationErrorOmitsMessageAndIsLanguageIndependent() {
        var mapper = new CaptchaWebMapper((code, locale, accountEnv, trustCode, schemeType) ->
                "server-resolved-" + locale + "-message");

        JsonNode en = objectMapper.valueToTree(mapper.missingAnswerResponse("en"));
        JsonNode zh = objectMapper.valueToTree(mapper.missingAnswerResponse("zh-HK"));

        assertThat(en).isEqualTo(zh);
        assertThat(en.path("errors").path(0).path("code").asText())
                .isEqualTo("err.registration.captcha.required");
        assertThat(en.path("errors").path(0).has("message")).isFalse();
        assertThat(en.path("messages")).isEmpty();
    }

    @Test
    void nullMessageIsOmittedWhileExplicitApimOwnedTextCanRemain() {
        var codeOnly = MutationResponse.<Void>failure(
                ApiStatus.SYSTEM_ERROR,
                List.of(ApiError.downstreamSystem(
                        "err.workflow.downstream.unavailable", null, "SERVER")));
        var apimOwned = MutationResponse.success(
                ApiStatus.SUCCESS,
                "payload",
                List.of(ApiMessage.info(
                        "apim.content.available", "APIM-owned localized content", "PAGE")));

        JsonNode codeOnlyJson = objectMapper.valueToTree(codeOnly);
        JsonNode apimOwnedJson = objectMapper.valueToTree(apimOwned);

        assertThat(codeOnlyJson.path("errors").path(0).has("message")).isFalse();
        assertThat(apimOwnedJson.path("messages").path(0).path("message").asText())
                .isEqualTo("APIM-owned localized content");
    }
}

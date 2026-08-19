package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration.RegistrationCancellationPageMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.registration.RegistrationSuccessPageMapper;
import com.bct.ngtpa.apiservice.application.dto.workflow.RegistrationWorkflowConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class FrontendOwnedLocalizationPageContractTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final BffPagesProperties pages = new BffPagesProperties();

  @Test
  void cancellationPageIsLanguageIndependentAndContainsSemanticActionsOnly() {
    var mapper = new RegistrationCancellationPageMapper(pages);
    JsonNode en = objectMapper.valueToTree(mapper.map("en"));
    JsonNode zh = objectMapper.valueToTree(mapper.map("zh-HK"));

    assertThat(en).isEqualTo(zh);
    assertThat(en.path("pageId").asText()).isEqualTo("registration-cancelled");
    assertThat(en.path("actions").path(0).path("id").asText()).isEqualTo("returnToLogin");
    assertNoResolvedDisplayText(en);
  }

  @Test
  void successPageKeepsVariantAndSemanticNavigationWithoutDisplayText() {
    var mapper = new RegistrationSuccessPageMapper(pages);
    JsonNode page =
        objectMapper.valueToTree(
            mapper.map(
                RegistrationWorkflowConstants.PAGE_REGISTRATION_SUCCESS,
                RegistrationWorkflowConstants.SUCCESS_VARIANT_HAS_REMAINING_TRUSTS,
                "zh-HK"));

    assertThat(page.path("variant").asText())
        .isEqualTo(RegistrationWorkflowConstants.SUCCESS_VARIANT_HAS_REMAINING_TRUSTS);
    assertThat(page.path("actions").path(0).path("id").asText()).isEqualTo("registerAnotherTrust");
    assertThat(page.path("actions").path(1).path("id").asText()).isEqualTo("returnToLogin");
    assertNoResolvedDisplayText(page);
  }

  private void assertNoResolvedDisplayText(JsonNode page) {
    assertThat(page.has("title")).isFalse();
    assertThat(page.has("body")).isFalse();
    assertThat(page.has("text")).isFalse();
    page.path("actions").forEach(action -> assertThat(action.has("label")).isFalse());
  }

  @Test
  void responseTypesDoNotExposeResolvedPresentationAccessors() {
    assertThat(
            methodNames(
                com.bct.ngtpa.apiservice.adapter.in.web.response
                    .RegistrationCancellationPageResponse.class))
        .doesNotContain("title", "body", "text");
    assertThat(
            methodNames(
                com.bct.ngtpa.apiservice.adapter.in.web.response.RegistrationSuccessPageResponse
                    .class))
        .doesNotContain("title", "body", "text");
    assertThat(
            methodNames(
                com.bct.ngtpa.apiservice.adapter.in.web.response
                    .RegistrationCancellationPageActionResponse.class))
        .doesNotContain("label");
    assertThat(
            methodNames(
                com.bct.ngtpa.apiservice.adapter.in.web.response
                    .RegistrationSuccessPageActionResponse.class))
        .doesNotContain("label");
  }

  private java.util.List<String> methodNames(Class<?> type) {
    return java.util.Arrays.stream(type.getMethods())
        .map(java.lang.reflect.Method::getName)
        .distinct()
        .toList();
  }
}

package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ConfirmationProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.RuleActionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.RuleConditionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ValidationRuleProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class YamlResponseMapperTest {

        private final YamlResponseMapper mapper = new YamlResponseMapper(new ObjectMapper());

        @Test
        void localizesNestedLabelMapsAndRemovesEmptyValues() {
                ConfirmationProperties confirmation = new ConfirmationProperties();
                confirmation.setEnabled(true);
                confirmation.setTitle(Map.of("en", "Confirm Information Update", "zh_HK", "確認個人資料更改"));
                confirmation.setReviewMessage(Map.of("en", "Review before submit", "zh_HK", "提交前請檢查"));
                confirmation.setSecurityVerification(Map.of(
                                "label", Map.of("en", "Security Verification", "zh_HK", "安全認證"),
                                "instruction", Map.of("en", "Enter password", "zh_HK", "請輸入密碼"),
                                "passwordField", Map.of(
                                                "name", "password",
                                                "dataType", "string",
                                                "controlType", "password",
                                                "required", true,
                                                "validations", List.of(Map.of("type", "required")))));

                Map<String, Object> response = mapper.toResponseMap(confirmation, "zh_HK");

                assertThat(response)
                                .containsEntry("enabled", true)
                                .containsEntry("title", "確認個人資料更改")
                                .containsEntry("reviewMessage", "提交前請檢查");
                assertThat(asMap(response.get("securityVerification")))
                                .containsEntry("label", "安全認證")
                                .containsEntry("instruction", "請輸入密碼");
                assertThat(asMap(asMap(response.get("securityVerification")).get("passwordField")))
                                .containsEntry("name", "password")
                                .containsEntry("required", true);
                assertThat(response).doesNotContainKey("message");
        }

        @Test
        void mapsValidationRulesUsingYamlShapeAndLocalization() {
                RuleConditionProperties when = new RuleConditionProperties();
                when.setOperator("notBlank");
                when.setField("emailAddress");

                RuleActionProperties then = new RuleActionProperties();
                then.setOperator("fail");
                then.setTargets(List.of("emailAddress"));

                ValidationRuleProperties rule = new ValidationRuleProperties();
                rule.setId("email.required");
                rule.setType("conditionalRequired");
                rule.setWhen(when);
                rule.setThen(then);
                rule.setSeverity("error");
                rule.setCode("personalInformation.email.required");
                rule.setMessage(Map.of("en", "Please provide a valid email address.", "zh_HK", "請輸入有效電郵地址。"));

                List<Map<String, Object>> response = mapper.toResponseList(List.of(rule), "zh_HK");

                assertThat(response).hasSize(1);
                assertThat(response.get(0))
                                .containsEntry("id", "email.required")
                                .containsEntry("type", "conditionalRequired")
                                .containsEntry("severity", "error")
                                .containsEntry("code", "personalInformation.email.required")
                                .containsEntry("message", "請輸入有效電郵地址。");
                assertThat(asMap(response.get(0).get("when"))).containsEntry("operator", "notBlank");
                assertThat(asMap(response.get(0).get("then"))).containsEntry("operator", "fail");
        }

        @Test
        void yamlMapperHandlesNullsFallbackLabelsSkippedKeysAndEmptyValues() {
                YamlResponseMapper mapper = new YamlResponseMapper(new ObjectMapper());

                assertThat(mapper.toResponseMap(null, "en")).isEmpty();
                assertThat(mapper.toResponseList(null, "en")).isEmpty();
                assertThat(mapper.toResponseList(List.of(), "en")).isEmpty();

                Map<String, Object> source = new LinkedHashMap<>();
                source.put("title", Map.of("zh-HK", "繁體標題"));
                source.put("fallbackTitle", Map.of("zh-HK", "Titre"));
                source.put("blank", "   ");
                source.put("emptyMap", Map.of());
                source.put("emptyList", List.of());
                source.put("apimBinding", Map.of("data", "email"));
                source.put("items", List.of(
                                Map.of("label", Map.of("en", "Visible")),
                                Map.of("label", "")));
                source.put("enabled", true);

                Map<String, Object> zhResponse = mapper.toResponseMap(source, "zh_HK");
                assertThat(zhResponse)
                                .containsEntry("title", "繁體標題")
                                .containsEntry("enabled", true);
                assertThat(zhResponse).doesNotContainKeys("blank", "emptyMap", "emptyList", "apimBinding");
                assertThat((List<?>) zhResponse.get("items")).hasSize(1);

                Map<String, Object> fallbackResponse = mapper.toResponseMap(source, "fr");
                assertThat(fallbackResponse).containsEntry("fallbackTitle", "Titre");
        }

        // Helpers
        @SuppressWarnings("unchecked")
        private Map<String, Object> asMap(Object value) {
                return (Map<String, Object>) value;
        }
}

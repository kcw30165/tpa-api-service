package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ConfirmationProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.RuleActionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.RuleConditionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ValidationRuleProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}

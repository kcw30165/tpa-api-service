package com.bct.ngtpa.apiservice.adapter.in.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.mapper.PageDisplayTextResolver;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FormMetadataProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.RuleActionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.RuleConditionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.SectionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ValidationRuleProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.shared.config.ConfigVariantCandidateGenerator;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalInformationUpdateYamlValidatorAddressTest {

    private static final String VALIDATOR_CLASS =
            "com.bct.ngtpa.apiservice.adapter.in.web.validation.PersonalInformationUpdateYamlValidator";

    @Test
    void validatorClassExistsAndUsesConfigVariantCandidateGenerator() throws Exception {
        Class.forName(VALIDATOR_CLASS);

        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/validation/PersonalInformationUpdateYamlValidator.java"));
        assertThat(source).contains("ConfigVariantCandidateGenerator");
        assertThat(source).contains("valueByVariant");
        assertThat(source).contains("messageCode");
    }

    @Test
    void blankResidentialAndMailingAddressReturnsConfiguredCrossFieldError() throws Exception {
        List<ApiError> errors = validate(Map.of(
                "emailAddress", "member@example.test"));

        assertThat(errors)
                .anySatisfy(error -> {
                    assertThat(error.type()).isEqualTo("CROSS_FIELD");
                    assertThat(error.code()).isEqualTo("personalInformation.address.atLeastOneRequired");
                    assertThat(error.message()).isEqualTo("Please input a residential address or correspondence address.");
                    assertThat(error.targets()).containsExactly("residentialAddressLine1", "mailingAddressLine1");
                    assertThat(error.severity()).isEqualTo("ERROR");
                    assertThat(error.source()).isEqualTo("SERVER");
                });
    }


    @Test
    void allGroupsEmptyWithMalformedGroupFieldsIsFailSafe() throws Exception {
        PageSchemaProperties page = properties().getPages().get("personalInformation");
        ValidationRuleProperties brokenGroupRule = page.getValidations().stream()
                .filter(rule -> "address.atLeastOneRequired".equals(rule.getId()))
                .findFirst()
                .orElseThrow();
        brokenGroupRule.getWhen().setGroups(List.of(Map.of("name", "residentialAddress", "fields", "residentialAddressLine1")));

        BffPagesProperties properties = new BffPagesProperties();
        properties.setPages(Map.of("personalInformation", page));
        PersonalInformationUpdateYamlValidator validator = new PersonalInformationUpdateYamlValidator(
                properties,
                new PageDisplayTextResolver(new ConfigVariantCandidateGenerator()),
                new ConfigVariantCandidateGenerator());

        List<ApiError> errors = validator.validate(
                new UpdatePersonalInformationRequest("1.0", false, Map.of(
                        "residentialAddressLine1", "Flat 15B, Block 2, Garden Villa Estate",
                        "residentialAddressLine2", "Tai Po, New Territories",
                        "residentialAddressLine3", "HK",
                        "residentialCountry", "HK")),
                "en",
                null,
                null,
                null);

        assertThat(errors)
                .extracting(ApiError::code)
                .doesNotContain("personalInformation.address.atLeastOneRequired");
    }

    @Test
    void allGroupsEmptyWithoutConfiguredGroupsIsFailSafe() throws Exception {
        PageSchemaProperties page = properties().getPages().get("personalInformation");
        ValidationRuleProperties brokenGroupRule = page.getValidations().stream()
                .filter(rule -> "address.atLeastOneRequired".equals(rule.getId()))
                .findFirst()
                .orElseThrow();
        brokenGroupRule.getWhen().setGroups(List.of());

        BffPagesProperties properties = new BffPagesProperties();
        properties.setPages(Map.of("personalInformation", page));
        PersonalInformationUpdateYamlValidator validator = new PersonalInformationUpdateYamlValidator(
                properties,
                new PageDisplayTextResolver(new ConfigVariantCandidateGenerator()),
                new ConfigVariantCandidateGenerator());

        List<ApiError> errors = validator.validate(
                new UpdatePersonalInformationRequest("1.0", false, Map.of(
                        "residentialAddressLine1", "Flat 15B, Block 2, Garden Villa Estate",
                        "residentialAddressLine2", "Tai Po, New Territories",
                        "residentialAddressLine3", "HK",
                        "residentialCountry", "HK")),
                "en",
                null,
                null,
                null);

        assertThat(errors)
                .extracting(ApiError::code)
                .doesNotContain("personalInformation.address.atLeastOneRequired");
    }

    @Test
    void mailingOnlyAddressWithCountryPassesAddressRequirement() throws Exception {
        List<ApiError> errors = validate(Map.of(
                "mailingAddressLine1", "12345 Mailing Road",
                "mailingCountry", "HK",
                "emailAddress", "member@example.test"));

        assertThat(errors)
                .noneMatch(error -> "personalInformation.address.atLeastOneRequired".equals(error.code()))
                .noneMatch(error -> error.targets().contains("residentialAddressLine1"))
                .noneMatch(error -> error.targets().contains("residentialCountry"));
    }

    @Test
    void residentialAddressCapturedWithoutResidentialCountryTargetsResidentialCountryOnly() throws Exception {
        List<ApiError> errors = validate(Map.of(
                "residentialAddressLine1", "12345 Residential Road",
                "emailAddress", "member@example.test"));

        assertThat(errors)
                .anySatisfy(error -> {
                    assertThat(error.code()).isEqualTo("personalInformation.residentialCountry.required");
                    assertThat(error.targets()).containsExactly("residentialCountry");
                });
        assertThat(errors)
                .noneMatch(error -> error.targets().contains("mailingCountry"))
                .noneMatch(error -> error.targets().contains("overseasCountryCode"));
    }

    @Test
    void mailingAddressCapturedWithoutMailingCountryTargetsMailingCountryOnly() throws Exception {
        List<ApiError> errors = validate(Map.of(
                "mailingAddressLine1", "12345 Mailing Road",
                "emailAddress", "member@example.test"));

        assertThat(errors)
                .anySatisfy(error -> {
                    assertThat(error.code()).isEqualTo("personalInformation.mailingCountry.required");
                    assertThat(error.targets()).containsExactly("mailingCountry");
                });
        assertThat(errors)
                .noneMatch(error -> error.targets().contains("residentialCountry"))
                .noneMatch(error -> error.targets().contains("overseasCountryCode"));
    }

    @Test
    void poBoxAddressReturnsConfiguredHotlineError() throws Exception {
        List<ApiError> errors = validate(Map.of(
                "mailingAddressLine1", "PO Box 123",
                "mailingCountry", "HK",
                "emailAddress", "member@example.test"));

        assertThat(errors)
                .anySatisfy(error -> {
                    assertThat(error.type()).isEqualTo("FIELD");
                    assertThat(error.code()).isEqualTo("personalInformation.address.poBoxBlocked");
                    assertThat(error.message()).isEqualTo(
                            "PO Box address will not be accepted. Please contact the hotline for assistance.");
                    assertThat(error.targets()).containsExactly("mailingAddressLine1");
                });
    }

    @SuppressWarnings("unchecked")
    private List<ApiError> validate(Map<String, Object> fields) throws Exception {
        Class<?> validatorClass = Class.forName(VALIDATOR_CLASS);
        Constructor<?> constructor = validatorClass.getConstructor(
                BffPagesProperties.class,
                PageDisplayTextResolver.class,
                ConfigVariantCandidateGenerator.class);
        Object validator = constructor.newInstance(
                properties(),
                new PageDisplayTextResolver(new ConfigVariantCandidateGenerator()),
                new ConfigVariantCandidateGenerator());
        Method validate = validatorClass.getMethod(
                "validate",
                UpdatePersonalInformationRequest.class,
                String.class,
                String.class,
                String.class,
                String.class);
        return (List<ApiError>) validate.invoke(
                validator,
                new UpdatePersonalInformationRequest("1.0", false, fields),
                "en",
                "JP",
                "JPM",
                "OE");
    }

    private BffPagesProperties properties() {
        PageSchemaProperties page = new PageSchemaProperties();
        page.setDisplay(Map.of(
                "personalInformation.address.atLeastOneRequired.message", Map.of(
                        "en", "Please input a residential address or correspondence address.",
                        "zh_HK", "請輸入住宅地址或通訊地址。"),
                "personalInformation.address.poBoxBlocked.message", Map.of(
                        "en", "PO Box address will not be accepted. Please contact the hotline for assistance.",
                        "zh_HK", "不接受郵政信箱地址。請聯絡熱線尋求協助。"),
                "personalInformation.residentialCountry.required.message", Map.of(
                        "en", "Please select the residential country/region.",
                        "zh_HK", "請選擇住宅地址國家／地區。"),
                "personalInformation.mailingCountry.required.message", Map.of(
                        "en", "Please select the correspondence country/region.",
                        "zh_HK", "請選擇通訊地址國家／地區。"),
                "personalInformation.residentialAddress.invalid.message", Map.of(
                        "en", "Please input a valid residential address.",
                        "zh_HK", "請輸入有效的住宅地址。"),
                "personalInformation.mailingAddress.invalid.message", Map.of(
                        "en", "Please input a valid correspondence address.",
                        "zh_HK", "請輸入有效的通訊地址。")));

        page.setForm(form(List.of(
                field("residentialAddressLine1", List.of(
                        minLength("residentialAddressLine1.minLength", 5, "personalInformation.residentialAddress.invalid", "personalInformation.residentialAddress.invalid.message"),
                        blocked("residentialAddressLine1.poBoxBlocked"))),
                field("residentialAddressLine2", List.of(blocked("residentialAddressLine2.poBoxBlocked"))),
                field("residentialAddressLine3", List.of(blocked("residentialAddressLine3.poBoxBlocked"))),
                field("residentialCountry", List.of()),
                field("mailingAddressLine1", List.of(
                        minLength("mailingAddressLine1.minLength", 5, "personalInformation.mailingAddress.invalid", "personalInformation.mailingAddress.invalid.message"),
                        blocked("mailingAddressLine1.poBoxBlocked"))),
                field("mailingAddressLine2", List.of(blocked("mailingAddressLine2.poBoxBlocked"))),
                field("mailingAddressLine3", List.of(blocked("mailingAddressLine3.poBoxBlocked"))),
                field("mailingCountry", List.of()),
                field("emailAddress", List.of()))));
        page.setValidations(List.of(
                addressAtLeastOneRequired(),
                residentialCountryRequired(),
                mailingCountryRequired()));

        BffPagesProperties properties = new BffPagesProperties();
        properties.setPages(Map.of("personalInformation", page));
        return properties;
    }

    private FormMetadataProperties form(List<FieldProperties> fields) {
        SectionProperties section = new SectionProperties();
        section.setId("addressInformation");
        section.setFields(fields);
        FormMetadataProperties form = new FormMetadataProperties();
        form.setSections(List.of(section));
        return form;
    }

    private FieldProperties field(String id, List<ValidationRuleProperties> validations) {
        FieldProperties field = new FieldProperties();
        field.setId(id);
        field.setValidations(validations);
        return field;
    }

    private ValidationRuleProperties minLength(String id, int value, String code, String messageCode) {
        ValidationRuleProperties rule = new ValidationRuleProperties();
        rule.setId(id);
        rule.setType("minLength");
        rule.setValue(value);
        rule.setCode(code);
        rule.setMessageCode(messageCode);
        rule.setSeverity("error");
        return rule;
    }

    private ValidationRuleProperties blocked(String id) {
        ValidationRuleProperties rule = new ValidationRuleProperties();
        rule.setId(id);
        rule.setType("blockedAddress");
        rule.setValue("PO Box~|~P.O. Box~|~P O Box~|~P.O Box~|~Post Office Box~|~P.O. Box~|~郵政信箱");
        rule.setCode("personalInformation.address.poBoxBlocked");
        rule.setMessageCode("personalInformation.address.poBoxBlocked.message");
        rule.setSeverity("error");
        return rule;
    }

    private ValidationRuleProperties addressAtLeastOneRequired() {
        ValidationRuleProperties rule = new ValidationRuleProperties();
        rule.setId("address.atLeastOneRequired");
        rule.setType("groupRequirement");
        RuleConditionProperties when = new RuleConditionProperties();
        when.setOperator("allGroupsEmpty");
        when.setGroups(List.of(
                Map.of("name", "residentialAddress", "fields", List.of(
                        "residentialAddressLine1", "residentialAddressLine2", "residentialAddressLine3", "residentialCountry")),
                Map.of("name", "mailingAddress", "fields", List.of(
                        "mailingAddressLine1", "mailingAddressLine2", "mailingAddressLine3", "mailingCountry"))));
        RuleActionProperties then = new RuleActionProperties();
        then.setOperator("fail");
        then.setTargets(List.of("residentialAddressLine1", "mailingAddressLine1"));
        rule.setWhen(when);
        rule.setThen(then);
        rule.setCode("personalInformation.address.atLeastOneRequired");
        rule.setMessageCode("personalInformation.address.atLeastOneRequired.message");
        rule.setSeverity("error");
        return rule;
    }

    private ValidationRuleProperties residentialCountryRequired() {
        return conditionalRequired(
                "residentialCountry.requiredWhenResidentialAddressCaptured",
                List.of("residentialAddressLine1", "residentialAddressLine2", "residentialAddressLine3"),
                List.of("residentialCountry"),
                "personalInformation.residentialCountry.required",
                "personalInformation.residentialCountry.required.message");
    }

    private ValidationRuleProperties mailingCountryRequired() {
        return conditionalRequired(
                "mailingCountry.requiredWhenMailingAddressCaptured",
                List.of("mailingAddressLine1", "mailingAddressLine2", "mailingAddressLine3"),
                List.of("mailingCountry"),
                "personalInformation.mailingCountry.required",
                "personalInformation.mailingCountry.required.message");
    }

    private ValidationRuleProperties conditionalRequired(
            String id,
            List<String> whenFields,
            List<String> targets,
            String code,
            String messageCode) {
        ValidationRuleProperties rule = new ValidationRuleProperties();
        rule.setId(id);
        rule.setType("conditionalRequired");
        RuleConditionProperties when = new RuleConditionProperties();
        when.setOperator("any");
        when.setFields(whenFields);
        RuleActionProperties then = new RuleActionProperties();
        then.setOperator("required");
        then.setTargets(targets);
        rule.setWhen(when);
        rule.setThen(then);
        rule.setCode(code);
        rule.setMessageCode(messageCode);
        rule.setSeverity("error");
        return rule;
    }
}

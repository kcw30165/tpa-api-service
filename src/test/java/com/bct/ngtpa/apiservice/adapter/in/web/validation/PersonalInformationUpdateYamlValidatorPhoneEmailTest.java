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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PersonalInformationUpdateYamlValidatorPhoneEmailTest {

    @Test
    void hongKongBusinessPhoneAndExtensionRulesReturnAllApplicableViolations() {
        List<ApiError> errors = validate(Map.of(
                "hongKongBusinessPhone", "12A",
                "hongKongBusinessPhoneExtension", "9B",
                "emailAddress", "member@example.test"));

        assertThat(errors)
                .anySatisfy(error -> assertError(
                        error,
                        "personalInformation.businessPhone.invalid",
                        "Please input a valid Hong Kong business phone number.",
                        "hongKongBusinessPhone"))
                .anySatisfy(error -> assertError(
                        error,
                        "personalInformation.businessPhoneExtension.invalid",
                        "Please input a valid Hong Kong business phone extension.",
                        "hongKongBusinessPhoneExtension"));
    }

    @Test
    void extensionWithoutBusinessPhoneTargetsBusinessPhone() {
        List<ApiError> errors = validate(Map.of(
                "hongKongBusinessPhoneExtension", "123",
                "emailAddress", "member@example.test"));

        assertThat(errors)
                .anySatisfy(error -> assertError(
                        error,
                        "personalInformation.businessPhone.requiredWhenExtensionPresent",
                        "Please input the Hong Kong business phone number when an extension is provided.",
                        "hongKongBusinessPhone"));
    }

    @Test
    void hongKongMobileMustBeIntegerAndStartWithFourToNine() {
        assertThat(validate(Map.of(
                "hongKongMobilePhone", "31234567",
                "emailAddress", "member@example.test")))
                .anySatisfy(error -> assertError(
                        error,
                        "personalInformation.mobilePhone.invalid",
                        "Please input a valid Hong Kong mobile phone number.",
                        "hongKongMobilePhone"));

        assertThat(validate(Map.of(
                "hongKongMobilePhone", "9A234567",
                "emailAddress", "member@example.test")))
                .anySatisfy(error -> assertError(
                        error,
                        "personalInformation.mobilePhone.invalid",
                        "Please input a valid Hong Kong mobile phone number.",
                        "hongKongMobilePhone"));
    }

    @Test
    void overseasPhoneRulesReturnSpecificTargetsAndDoNotMixAddressCountries() {
        List<ApiError> codeWithoutPhoneErrors = validate(Map.of(
                "overseasCountryCode", "852",
                "emailAddress", "member@example.test"));

        assertThat(codeWithoutPhoneErrors)
                .anySatisfy(error -> assertError(
                        error,
                        "personalInformation.overseasPhoneNumber.requiredWhenCodePresent",
                        "Please input the overseas phone number when an overseas country or area code is provided.",
                        "overseasPhoneNumber"));

        List<ApiError> phoneWithoutCodeErrors = validate(Map.of(
                "overseasPhoneNumber", "12345678",
                "emailAddress", "member@example.test"));

        assertThat(phoneWithoutCodeErrors)
                .anySatisfy(error -> assertError(
                        error,
                        "personalInformation.overseasCountryCode.requiredWhenPhonePresent",
                        "Please input the overseas country code when an overseas phone number is provided.",
                        "overseasCountryCode"));
        assertThat(phoneWithoutCodeErrors)
                .noneMatch(error -> error.targets().contains("residentialCountry"))
                .noneMatch(error -> error.targets().contains("mailingCountry"));
    }

    @Test
    void overseasPhoneAndFaxPatternsReturnFieldSpecificErrors() {
        List<ApiError> errors = validate(Map.of(
                "overseasCountryCode", "8A2",
                "overseasAreaCode", "B3",
                "overseasPhoneNumber", "12C45",
                "faxNo", "12#45",
                "emailAddress", "member@example.test"));

        assertThat(errors)
                .anySatisfy(error -> assertError(error, "personalInformation.overseasCountryCode.invalid", "Please input a valid overseas country code.", "overseasCountryCode"))
                .anySatisfy(error -> assertError(error, "personalInformation.overseasAreaCode.invalid", "Please input a valid overseas area code.", "overseasAreaCode"))
                .anySatisfy(error -> assertError(error, "personalInformation.overseasPhoneNumber.invalid", "Please input a valid overseas phone number.", "overseasPhoneNumber"))
                .anySatisfy(error -> assertError(error, "personalInformation.faxNo.invalid", "Please input a valid fax number.", "faxNo"));
    }

    @Test
    void blankEmailReturnsRequiredOnlyAndInvalidEmailReturnsEmailError() {
        List<ApiError> blankErrors = validate(Map.of());
        assertThat(blankErrors)
                .anySatisfy(error -> assertError(
                        error,
                        "personalInformation.email.required",
                        "Please input a valid email address.",
                        "emailAddress"));
        assertThat(blankErrors)
                .noneMatch(error -> "personalInformation.email.invalid".equals(error.code()))
                .noneMatch(error -> "personalInformation.email.tooShort".equals(error.code()));

        List<ApiError> invalidErrors = validate(Map.of("emailAddress", "bad email"));
        assertThat(invalidErrors)
                .anySatisfy(error -> assertError(
                        error,
                        "personalInformation.email.invalid",
                        "Please input a valid email address.",
                        "emailAddress"));
    }

    @Test
    void jpEmailUsesValueByVariantAndJpDisplayVariantMessage() {
        List<ApiError> errors = validate(Map.of("emailAddress", "a@b.co"));

        assertThat(errors)
                .anySatisfy(error -> assertError(
                        error,
                        "personalInformation.email.tooShort",
                        "For JP accounts, please input an email address with more than 10 characters.",
                        "emailAddress"));
    }

    @Test
    void validPhoneFaxAndEmailPayloadReturnsNoPhoneEmailErrors() {
        List<ApiError> errors = validate(Map.of(
                "hongKongBusinessPhone", "21234567",
                "hongKongBusinessPhoneExtension", "123",
                "hongKongMobilePhone", "91234567",
                "overseasCountryCode", "852",
                "overseasAreaCode", "2",
                "overseasPhoneNumber", "23456789",
                "faxNo", "+852 2345-6789",
                "emailAddress", "valid.member@example.test"));

        assertThat(errors)
                .noneMatch(error -> error.code().startsWith("personalInformation.businessPhone"))
                .noneMatch(error -> error.code().startsWith("personalInformation.mobilePhone"))
                .noneMatch(error -> error.code().startsWith("personalInformation.overseas"))
                .noneMatch(error -> error.code().startsWith("personalInformation.faxNo"))
                .noneMatch(error -> error.code().startsWith("personalInformation.email"));
    }

    private List<ApiError> validate(Map<String, Object> fields) {
        PersonalInformationUpdateYamlValidator validator = new PersonalInformationUpdateYamlValidator(
                properties(),
                new PageDisplayTextResolver(new ConfigVariantCandidateGenerator()),
                new ConfigVariantCandidateGenerator());
        return validator.validate(new UpdatePersonalInformationRequest("1.0", false, fields), "en", "JP", "JPM", "OE");
    }

    private void assertError(ApiError error, String code, String message, String target) {
        assertThat(error.type()).isEqualTo("FIELD");
        assertThat(error.code()).isEqualTo(code);
        assertThat(error.message()).isEqualTo(message);
        assertThat(error.targets()).containsExactly(target);
        assertThat(error.severity()).isEqualTo("ERROR");
        assertThat(error.source()).isEqualTo("SERVER");
    }

    private BffPagesProperties properties() {
        PageSchemaProperties page = new PageSchemaProperties();
        page.setDisplay(display());
        page.setForm(form(List.of(
                field("hongKongBusinessPhone", List.of(pattern("hongKongBusinessPhone.integer", "^[0-9]*$", "personalInformation.businessPhone.invalid", "personalInformation.businessPhone.invalid.message"))),
                field("hongKongBusinessPhoneExtension", List.of(pattern("hongKongBusinessPhoneExtension.integer", "^[0-9]*$", "personalInformation.businessPhoneExtension.invalid", "personalInformation.businessPhoneExtension.invalid.message"))),
                field("hongKongMobilePhone", List.of(pattern("hongKongMobilePhone.hkMobile", "^[4-9][0-9]*$", "personalInformation.mobilePhone.invalid", "personalInformation.mobilePhone.invalid.message"))),
                field("overseasCountryCode", List.of(pattern("overseasCountryCode.integer", "^[0-9]*$", "personalInformation.overseasCountryCode.invalid", "personalInformation.overseasCountryCode.invalid.message"))),
                field("overseasAreaCode", List.of(pattern("overseasAreaCode.integer", "^[0-9]*$", "personalInformation.overseasAreaCode.invalid", "personalInformation.overseasAreaCode.invalid.message"))),
                field("overseasPhoneNumber", List.of(pattern("overseasPhoneNumber.integer", "^[0-9]*$", "personalInformation.overseasPhoneNumber.invalid", "personalInformation.overseasPhoneNumber.invalid.message"))),
                field("faxNo", List.of(pattern("faxNo.permittedCharacters", "^[0-9+()\\-\\s]*$", "personalInformation.faxNo.invalid", "personalInformation.faxNo.invalid.message"))),
                field("emailAddress", List.of(emailRequired(), emailInvalid(), emailMinLength())))));
        page.setValidations(List.of(
                businessPhoneRequiredWhenExtensionPresent(),
                overseasPhoneRequiredWhenCodePresent(),
                overseasCountryCodeRequiredWhenPhonePresent()));
        BffPagesProperties properties = new BffPagesProperties();
        properties.setPages(Map.of("personalInformation", page));
        return properties;
    }

    private Map<String, Map<String, String>> display() {
        return Map.ofEntries(
                Map.entry("personalInformation.businessPhone.invalid.message", Map.of("en", "Please input a valid Hong Kong business phone number.")),
                Map.entry("personalInformation.businessPhoneExtension.invalid.message", Map.of("en", "Please input a valid Hong Kong business phone extension.")),
                Map.entry("personalInformation.businessPhone.requiredWhenExtensionPresent.message", Map.of("en", "Please input the Hong Kong business phone number when an extension is provided.")),
                Map.entry("personalInformation.mobilePhone.invalid.message", Map.of("en", "Please input a valid Hong Kong mobile phone number.")),
                Map.entry("personalInformation.overseasCountryCode.invalid.message", Map.of("en", "Please input a valid overseas country code.")),
                Map.entry("personalInformation.overseasAreaCode.invalid.message", Map.of("en", "Please input a valid overseas area code.")),
                Map.entry("personalInformation.overseasPhoneNumber.invalid.message", Map.of("en", "Please input a valid overseas phone number.")),
                Map.entry("personalInformation.overseasPhoneNumber.requiredWhenCodePresent.message", Map.of("en", "Please input the overseas phone number when an overseas country or area code is provided.")),
                Map.entry("personalInformation.overseasCountryCode.requiredWhenPhonePresent.message", Map.of("en", "Please input the overseas country code when an overseas phone number is provided.")),
                Map.entry("personalInformation.faxNo.invalid.message", Map.of("en", "Please input a valid fax number.")),
                Map.entry("personalInformation.email.required.message", Map.of("en", "Please input a valid email address.")),
                Map.entry("personalInformation.email.invalid.message", Map.of("en", "Please input a valid email address.")),
                Map.entry("personalInformation.email.tooShort.message", Map.of("en", "Please input a valid email address.")),
                Map.entry("personalInformation.email.tooShort.message.JP", Map.of("en", "For JP accounts, please input an email address with more than 10 characters.")));
    }

    private FormMetadataProperties form(List<FieldProperties> fields) {
        SectionProperties section = new SectionProperties();
        section.setId("phoneEmailInformation");
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

    private ValidationRuleProperties pattern(String id, String value, String code, String messageCode) {
        ValidationRuleProperties rule = baseRule(id, "pattern", code, messageCode);
        rule.setValue(value);
        return rule;
    }

    private ValidationRuleProperties emailRequired() {
        return baseRule("email.required", "required", "personalInformation.email.required", "personalInformation.email.required.message");
    }

    private ValidationRuleProperties emailInvalid() {
        ValidationRuleProperties rule = baseRule("email.invalid", "email", "personalInformation.email.invalid", "personalInformation.email.invalid.message");
        rule.setValue(120);
        return rule;
    }

    private ValidationRuleProperties emailMinLength() {
        ValidationRuleProperties rule = baseRule("email.minLength", "minLength", "personalInformation.email.tooShort", "personalInformation.email.tooShort.message");
        rule.setValue(1);
        rule.setValueByVariant(Map.of("JP", 11));
        return rule;
    }

    private ValidationRuleProperties businessPhoneRequiredWhenExtensionPresent() {
        return conditionalRequired(
                "businessPhone.requiredWhenExtensionPresent",
                "notBlank",
                List.of("hongKongBusinessPhoneExtension"),
                List.of("hongKongBusinessPhone"),
                "personalInformation.businessPhone.requiredWhenExtensionPresent",
                "personalInformation.businessPhone.requiredWhenExtensionPresent.message");
    }

    private ValidationRuleProperties overseasPhoneRequiredWhenCodePresent() {
        return conditionalRequired(
                "overseasPhoneNumber.requiredWhenCodePresent",
                "any",
                List.of("overseasCountryCode", "overseasAreaCode"),
                List.of("overseasPhoneNumber"),
                "personalInformation.overseasPhoneNumber.requiredWhenCodePresent",
                "personalInformation.overseasPhoneNumber.requiredWhenCodePresent.message");
    }

    private ValidationRuleProperties overseasCountryCodeRequiredWhenPhonePresent() {
        return conditionalRequired(
                "overseasCountryCode.requiredWhenOverseasPhonePresent",
                "notBlank",
                List.of("overseasPhoneNumber"),
                List.of("overseasCountryCode"),
                "personalInformation.overseasCountryCode.requiredWhenPhonePresent",
                "personalInformation.overseasCountryCode.requiredWhenPhonePresent.message");
    }

    private ValidationRuleProperties conditionalRequired(
            String id,
            String operator,
            List<String> whenFields,
            List<String> targets,
            String code,
            String messageCode) {
        ValidationRuleProperties rule = baseRule(id, "conditionalRequired", code, messageCode);
        RuleConditionProperties when = new RuleConditionProperties();
        when.setOperator(operator);
        when.setFields(whenFields);
        RuleActionProperties then = new RuleActionProperties();
        then.setOperator("required");
        then.setTargets(targets);
        rule.setWhen(when);
        rule.setThen(then);
        return rule;
    }

    private ValidationRuleProperties baseRule(String id, String type, String code, String messageCode) {
        ValidationRuleProperties rule = new ValidationRuleProperties();
        rule.setId(id);
        rule.setType(type);
        rule.setCode(code);
        rule.setMessageCode(messageCode);
        rule.setSeverity("error");
        return rule;
    }
}

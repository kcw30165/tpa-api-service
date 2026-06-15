package com.bct.ngtpa.apiservice.adapter.in.web.validation;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PersonalInformationUpdateYamlValidator {

    private static final String PAGE_KEY = "personalInformation";
    private static final String TYPE_FIELD = "FIELD";
    private static final String TYPE_CROSS_FIELD = "CROSS_FIELD";
    private static final String SEVERITY_ERROR = "ERROR";
    private static final String SOURCE_SERVER = "SERVER";
    private static final String DELIMITED_BLOCKED_TERMS = "~|~";

    private final BffPagesProperties pagesProperties;
    private final PageDisplayTextResolver pageDisplayTextResolver;
    private final ConfigVariantCandidateGenerator candidateGenerator;

    public PersonalInformationUpdateYamlValidator(
            BffPagesProperties pagesProperties,
            PageDisplayTextResolver pageDisplayTextResolver,
            ConfigVariantCandidateGenerator candidateGenerator) {
        this.pagesProperties = pagesProperties;
        this.pageDisplayTextResolver = pageDisplayTextResolver;
        this.candidateGenerator = candidateGenerator;
    }

    public List<ApiError> validate(
            UpdatePersonalInformationRequest request,
            String language,
            String accountEnv,
            String trustCode,
            String schemeType) {
        PageSchemaProperties page = personalInformationPage();
        if (page == null || request == null) {
            return List.of();
        }

        Map<String, Object> submittedFields = request.fields() == null ? Map.of() : request.fields();
        Map<String, FieldProperties> fieldsById = fieldsById(page);
        List<ApiError> errors = new ArrayList<>();

        for (FieldProperties field : fieldsById.values()) {
            if (field == null || isBlank(field.getId()) || field.getValidations() == null) {
                continue;
            }
            Object value = submittedFields.get(field.getId());
            for (ValidationRuleProperties rule : field.getValidations()) {
                validateFieldRule(errors, page, field.getId(), value, rule, language, accountEnv, trustCode, schemeType);
            }
        }

        if (page.getValidations() != null) {
            for (ValidationRuleProperties rule : page.getValidations()) {
                validatePageRule(errors, page, submittedFields, rule, language, accountEnv, trustCode, schemeType);
            }
        }

        return deduplicate(errors);
    }

    private PageSchemaProperties personalInformationPage() {
        if (pagesProperties == null || pagesProperties.getPages() == null) {
            return null;
        }
        return pagesProperties.getPages().get(PAGE_KEY);
    }

    private Map<String, FieldProperties> fieldsById(PageSchemaProperties page) {
        Map<String, FieldProperties> fields = new LinkedHashMap<>();
        FormMetadataProperties form = page.getForm();
        if (form == null || form.getSections() == null) {
            return fields;
        }
        for (SectionProperties section : form.getSections()) {
            if (section == null || section.getFields() == null) {
                continue;
            }
            for (FieldProperties field : section.getFields()) {
                if (field != null && !isBlank(field.getId())) {
                    fields.put(field.getId(), field);
                }
            }
        }
        return fields;
    }

    private void validateFieldRule(
            List<ApiError> errors,
            PageSchemaProperties page,
            String fieldId,
            Object value,
            ValidationRuleProperties rule,
            String language,
            String accountEnv,
            String trustCode,
            String schemeType) {
        if (rule == null || isBlank(rule.getType())) {
            return;
        }
        String type = rule.getType();
        if (!"required".equals(type) && isBlankValue(value)) {
            return;
        }
        boolean failed = switch (type) {
            case "required" -> isBlankValue(value);
            case "minLength" -> stringValue(value).length() < intValue(resolveRuleValue(rule, accountEnv, trustCode, schemeType), 0);
            case "maxLength" -> stringValue(value).length() > intValue(resolveRuleValue(rule, accountEnv, trustCode, schemeType), Integer.MAX_VALUE);
            case "pattern" -> !Pattern.compile(stringValue(resolveRuleValue(rule, accountEnv, trustCode, schemeType))).matcher(stringValue(value)).matches();
            case "email" -> !isValidEmail(stringValue(value), resolveRuleValue(rule, accountEnv, trustCode, schemeType));
            case "blockedAddress" -> isBlockedAddress(stringValue(value), stringValue(resolveRuleValue(rule, accountEnv, trustCode, schemeType)));
            default -> false;
        };
        if (failed) {
            errors.add(error(TYPE_FIELD, page, rule, List.of(fieldId), language, accountEnv, trustCode, schemeType));
        }
    }

    private void validatePageRule(
            List<ApiError> errors,
            PageSchemaProperties page,
            Map<String, Object> submittedFields,
            ValidationRuleProperties rule,
            String language,
            String accountEnv,
            String trustCode,
            String schemeType) {
        if (rule == null || isBlank(rule.getType())) {
            return;
        }
        if ("groupRequirement".equals(rule.getType()) && conditionMatches(rule.getWhen(), submittedFields)) {
            List<String> targets = targets(rule.getThen());
            errors.add(error(TYPE_CROSS_FIELD, page, rule, targets, language, accountEnv, trustCode, schemeType));
            return;
        }
        if ("conditionalRequired".equals(rule.getType()) && conditionMatches(rule.getWhen(), submittedFields)) {
            for (String target : targets(rule.getThen())) {
                if (isBlankValue(submittedFields.get(target))) {
                    errors.add(error(TYPE_FIELD, page, rule, List.of(target), language, accountEnv, trustCode, schemeType));
                }
            }
        }
    }

    private boolean conditionMatches(RuleConditionProperties condition, Map<String, Object> submittedFields) {
        if (condition == null || isBlank(condition.getOperator())) {
            return false;
        }
        return switch (condition.getOperator()) {
            case "any" -> fields(condition).stream().anyMatch(field -> !isBlankValue(submittedFields.get(field)));
            case "all" -> !fields(condition).isEmpty()
                    && fields(condition).stream().allMatch(field -> !isBlankValue(submittedFields.get(field)));
            case "notBlank" -> fields(condition).stream().anyMatch(field -> !isBlankValue(submittedFields.get(field)));
            case "blank" -> fields(condition).stream().allMatch(field -> isBlankValue(submittedFields.get(field)));
            case "allGroupsEmpty" -> allGroupsEmpty(condition, submittedFields);
            default -> false;
        };
    }

    private boolean allGroupsEmpty(RuleConditionProperties condition, Map<String, Object> submittedFields) {
        List<Map<String, Object>> configuredGroups = groups(condition);
        if (configuredGroups.isEmpty()) {
            return false;
        }
        return configuredGroups.stream().allMatch(group -> groupIsEmpty(group, submittedFields));
    }

    private boolean groupIsEmpty(Map<String, Object> group, Map<String, Object> submittedFields) {
        Object rawFields = group == null ? null : group.get("fields");
        if (!(rawFields instanceof List<?> fieldRefs) || fieldRefs.isEmpty()) {
            return false;
        }
        for (Object fieldRef : fieldRefs) {
            if (fieldRef != null && !isBlankValue(submittedFields.get(fieldRef.toString()))) {
                return false;
            }
        }
        return true;
    }

    private ApiError error(
            String type,
            PageSchemaProperties page,
            ValidationRuleProperties rule,
            List<String> targets,
            String language,
            String accountEnv,
            String trustCode,
            String schemeType) {
        String code = defaultString(rule.getCode(), defaultString(rule.getId(), rule.getType()));
        String message = resolveMessage(page, rule, language, accountEnv, trustCode, schemeType);
        return new ApiError(type, code, message, targets, SEVERITY_ERROR, SOURCE_SERVER);
    }

    private String resolveMessage(
            PageSchemaProperties page,
            ValidationRuleProperties rule,
            String language,
            String accountEnv,
            String trustCode,
            String schemeType) {
        String messageCode = rule.getMessageCode();
        if (!isBlank(messageCode) && pageDisplayTextResolver != null) {
            String resolved = pageDisplayTextResolver.resolve(
                    page.getDisplay(), messageCode, rule.getMessage(), language, accountEnv, trustCode, schemeType);
            if (!isBlank(resolved)) {
                return resolved;
            }
        }
        if (rule.getMessage() != null) {
            String direct = rule.getMessage().get(language);
            if (!isBlank(direct)) {
                return direct;
            }
            direct = rule.getMessage().get("en");
            if (!isBlank(direct)) {
                return direct;
            }
        }
        return defaultString(messageCode, defaultString(rule.getCode(), rule.getType()));
    }

    private Object resolveRuleValue(ValidationRuleProperties rule, String accountEnv, String trustCode, String schemeType) {
        Map<String, Object> valueByVariant = rule.getValueByVariant();
        if (valueByVariant == null || valueByVariant.isEmpty()) {
            return rule.getValue();
        }
        for (String candidate : variantCandidates(accountEnv, trustCode, schemeType)) {
            if (valueByVariant.containsKey(candidate)) {
                return valueByVariant.get(candidate);
            }
        }
        return rule.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<String> variantCandidates(String accountEnv, String trustCode, String schemeType) {
        if (candidateGenerator != null) {
            try {
                Class<?> contextClass = Class.forName("com.bct.ngtpa.apiservice.shared.config.ConfigLookupContext");
                Object context = instantiateContext(contextClass, accountEnv, trustCode, schemeType);
                if (context != null) {
                    Method generate = candidateGenerator.getClass().getMethod("generate", contextClass);
                    Object result = generate.invoke(candidateGenerator, context);
                    if (result instanceof List<?> list) {
                        return (List<String>) list;
                    }
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall back to the documented DSL order below if the runtime context signature differs.
            }
        }
        return fallbackVariantCandidates(accountEnv, trustCode, schemeType);
    }

    private Object instantiateContext(Class<?> contextClass, String accountEnv, String trustCode, String schemeType)
            throws ReflectiveOperationException {
        for (Constructor<?> constructor : contextClass.getConstructors()) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length == 4 && Locale.class.isAssignableFrom(parameterTypes[3])) {
                return constructor.newInstance(accountEnv, trustCode, schemeType, Locale.ENGLISH);
            }
            if (parameterTypes.length == 3) {
                return constructor.newInstance(accountEnv, trustCode, schemeType);
            }
        }
        return null;
    }

    private List<String> fallbackVariantCandidates(String accountEnv, String trustCode, String schemeType) {
        List<String> candidates = new ArrayList<>();
        addJoined(candidates, accountEnv, trustCode, schemeType);
        addJoined(candidates, accountEnv, trustCode);
        addJoined(candidates, accountEnv, schemeType);
        addJoined(candidates, trustCode, schemeType);
        addSingle(candidates, accountEnv);
        addSingle(candidates, trustCode);
        addSingle(candidates, schemeType);
        return candidates;
    }

    private void addJoined(List<String> candidates, String... segments) {
        List<String> clean = new ArrayList<>();
        for (String segment : segments) {
            if (isBlank(segment)) {
                return;
            }
            clean.add(segment.trim());
        }
        String candidate = String.join(".", clean);
        if (!candidate.isBlank() && !candidates.contains(candidate)) {
            candidates.add(candidate);
        }
    }

    private void addSingle(List<String> candidates, String value) {
        if (!isBlank(value) && !candidates.contains(value.trim())) {
            candidates.add(value.trim());
        }
    }

    private boolean isValidEmail(String value, Object maxLengthValue) {
        if (isBlank(value)) {
            return true;
        }
        int maxLength = intValue(maxLengthValue, Integer.MAX_VALUE);
        if (value.length() > maxLength) {
            return false;
        }
        if (value.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        int firstAt = value.indexOf('@');
        if (firstAt <= 0 || firstAt != value.lastIndexOf('@')) {
            return false;
        }
        String localPart = value.substring(0, firstAt);
        String domainPart = value.substring(firstAt + 1);
        if (localPart.isBlank() || domainPart.isBlank()) {
            return false;
        }
        if (!domainPart.contains(".")) {
            return false;
        }
        return Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matcher(value).matches();
    }

    private boolean isBlockedAddress(String value, String configuredValue) {
        if (isBlank(configuredValue)) {
            return false;
        }
        if (configuredValue.contains(DELIMITED_BLOCKED_TERMS)) {
            String normalizedValue = value.toLowerCase(Locale.ROOT);
            for (String term : configuredValue.split(Pattern.quote(DELIMITED_BLOCKED_TERMS))) {
                if (!term.isBlank() && normalizedValue.contains(term.trim().toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
        return Pattern.compile(configuredValue, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(value).find();
    }

    private List<ApiError> deduplicate(List<ApiError> errors) {
        List<ApiError> deduplicated = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ApiError error : errors) {
            String key = error.type() + "|" + error.code() + "|" + error.message() + "|" + error.targets()
                    + "|" + error.severity() + "|" + error.source();
            if (seen.add(key)) {
                deduplicated.add(error);
            }
        }
        return List.copyOf(deduplicated);
    }

    private List<String> fields(RuleConditionProperties condition) {
        if (condition == null) {
            return List.of();
        }
        if (condition.getFields() != null) {
            return condition.getFields();
        }
        if (!isBlank(condition.getField())) {
            return List.of(condition.getField());
        }
        return List.of();
    }

    private List<Map<String, Object>> groups(RuleConditionProperties condition) {
        return condition == null || condition.getGroups() == null ? List.of() : condition.getGroups();
    }

    private List<String> targets(RuleActionProperties action) {
        if (action == null) {
            return List.of();
        }
        if (action.getTargets() != null) {
            return action.getTargets();
        }
        if (action.getFields() != null) {
            return action.getFields();
        }
        if (!isBlank(action.getField())) {
            return List.of(action.getField());
        }
        return List.of();
    }

    private boolean isBlankValue(Object value) {
        return value == null || value.toString().trim().isEmpty();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }
}

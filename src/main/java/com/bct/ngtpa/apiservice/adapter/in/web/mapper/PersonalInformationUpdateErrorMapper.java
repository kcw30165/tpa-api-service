package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ApimBindingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.SectionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ValidationRuleProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class PersonalInformationUpdateErrorMapper {

    private static final String PAGE_KEY = "personalInformation";
    private static final String SOURCE_SERVER = "SERVER";

    private final BffPagesProperties properties;

    public PersonalInformationUpdateErrorMapper(BffPagesProperties properties) {
        this.properties = properties;
    }

    public List<ApiError> toApiErrors(List<UpdatePersonalInformationError> rawErrors) {
        if (rawErrors == null || rawErrors.isEmpty()) {
            return List.of(ApiError.business(
                    "personalInformation.update.validationFailed",
                    "The submitted information is invalid.",
                    List.of(),
                    SOURCE_SERVER));
        }

        Map<String, String> frontendFieldByApimField = frontendFieldByApimField();
        List<ApiError> result = new ArrayList<>();
        for (UpdatePersonalInformationError raw : rawErrors) {
            if (raw == null) {
                continue;
            }
            List<String> targets = toTargets(raw.fields(), frontendFieldByApimField);
            String type = normalizeType(raw.type());
            String code = resolveCode(raw.code(), targets, type);
            String message = resolveMessage(code, raw.code(), targets, type);
            result.add(new ApiError(type, code, message, targets, "ERROR", SOURCE_SERVER));
        }
        return List.copyOf(result);
    }

    private List<String> toTargets(List<String> rawFields, Map<String, String> frontendFieldByApimField) {
        if (rawFields == null || rawFields.isEmpty()) {
            return List.of();
        }
        return rawFields.stream()
                .filter(Objects::nonNull)
                .flatMap(value -> Arrays.stream(value.split("\\|")))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> frontendFieldByApimField.getOrDefault(value, value))
                .distinct()
                .toList();
    }

    private Map<String, String> frontendFieldByApimField() {
        Map<String, String> result = new LinkedHashMap<>();
        PageSchemaProperties page = properties == null || properties.getPages() == null
                ? null
                : properties.getPages().get(PAGE_KEY);
        if (page == null || page.getForm() == null || page.getForm().getSections() == null) {
            return result;
        }
        for (SectionProperties section : page.getForm().getSections()) {
            if (section == null || section.getFields() == null) {
                continue;
            }
            for (FieldProperties field : section.getFields()) {
                if (field == null || field.getId() == null || field.getId().isBlank()) {
                    continue;
                }
                ApimBindingProperties binding = field.getApimBinding();
                if (binding == null) {
                    continue;
                }
                putReverse(result, binding.getData(), field.getId());
                putReverse(result, binding.getConfig(), field.getId());
            }
        }
        return result;
    }

    private void putReverse(Map<String, String> result, String apimField, String frontendField) {
        if (apimField != null && !apimField.isBlank()) {
            result.putIfAbsent(apimField.trim(), frontendField);
        }
    }

    private String normalizeType(String value) {
        if (value == null || value.isBlank()) {
            return "FORM";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "FIELD", "CROSS_FIELD", "FORM", "BUSINESS", "SYSTEM" -> normalized;
            default -> "FORM";
        };
    }

    private String resolveCode(String rawCode, List<String> targets, String type) {
        String normalized = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if ("AT_LEAST_ONE_REQUIRED".equals(normalized)) {
            return "personalInformation.address.atLeastOneRequired";
        }
        if (!targets.isEmpty()) {
            String fromYaml = findValidationCode(targets.get(0), normalized);
            if (fromYaml != null) {
                return fromYaml;
            }
        }
        String field = targets.isEmpty() ? "field" : targets.get(0);
        return switch (normalized) {
            case "REQUIRED" -> "personalInformation." + toCodeToken(field) + ".required";
            case "INVALID_FORMAT" -> "personalInformation." + toCodeToken(field) + ".invalid";
            default -> rawCode == null || rawCode.isBlank()
                    ? "personalInformation.update.validationFailed"
                    : rawCode.trim();
        };
    }

    private String findValidationCode(String frontendField, String normalizedBackendCode) {
        FieldProperties field = findField(frontendField);
        if (field == null || field.getValidations() == null) {
            return null;
        }
        for (ValidationRuleProperties rule : field.getValidations()) {
            if (rule == null || rule.getCode() == null || rule.getCode().isBlank()) {
                continue;
            }
            String type = rule.getType() == null ? "" : rule.getType().trim().toUpperCase(Locale.ROOT);
            if (("REQUIRED".equals(normalizedBackendCode) && "REQUIRED".equals(type))
                    || ("INVALID_FORMAT".equals(normalizedBackendCode)
                            && ("EMAIL".equals(type) || "PATTERN".equals(type)))) {
                return rule.getCode();
            }
        }
        return null;
    }

    private String resolveMessage(String resolvedCode, String rawCode, List<String> targets, String type) {
        if ("personalInformation.address.atLeastOneRequired".equals(resolvedCode)) {
            return "Please input a residential address or correspondence address.";
        }
        if (!targets.isEmpty()) {
            String fromYaml = findValidationMessage(targets.get(0), rawCode);
            if (fromYaml != null && !fromYaml.isBlank()) {
                return fromYaml;
            }
        }
        String normalized = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "REQUIRED" -> "This field is required.";
            case "INVALID_FORMAT" -> "Please input a valid value.";
            default -> "The submitted information is invalid.";
        };
    }

    private String findValidationMessage(String frontendField, String rawCode) {
        String normalizedBackendCode = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        FieldProperties field = findField(frontendField);
        if (field == null || field.getValidations() == null) {
            return null;
        }
        for (ValidationRuleProperties rule : field.getValidations()) {
            if (rule == null || rule.getMessage() == null || rule.getMessage().isEmpty()) {
                continue;
            }
            String type = rule.getType() == null ? "" : rule.getType().trim().toUpperCase(Locale.ROOT);
            if (("REQUIRED".equals(normalizedBackendCode) && "REQUIRED".equals(type))
                    || ("INVALID_FORMAT".equals(normalizedBackendCode)
                            && ("EMAIL".equals(type) || "PATTERN".equals(type)))) {
                String en = rule.getMessage().get("en");
                return en != null ? en : rule.getMessage().values().stream().findFirst().orElse(null);
            }
        }
        return null;
    }

    private FieldProperties findField(String frontendField) {
        PageSchemaProperties page = properties == null || properties.getPages() == null
                ? null
                : properties.getPages().get(PAGE_KEY);
        if (page == null || page.getForm() == null || page.getForm().getSections() == null) {
            return null;
        }
        for (SectionProperties section : page.getForm().getSections()) {
            if (section == null || section.getFields() == null) {
                continue;
            }
            for (FieldProperties field : section.getFields()) {
                if (field != null && frontendField.equals(field.getId())) {
                    return field;
                }
            }
        }
        return null;
    }

    private String toCodeToken(String field) {
        if (field == null || field.isBlank()) {
            return "field";
        }
        String normalized = field.trim();
        if (normalized.endsWith("Address")) {
            return normalized;
        }
        return normalized;
    }
}

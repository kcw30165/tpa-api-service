package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ApimBindingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.SectionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ValidationRuleProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PersonalInformationUpdateErrorMapper {
  private static final Logger log =
      LoggerFactory.getLogger(PersonalInformationUpdateErrorMapper.class);

  private static final String CODE_REQUIRED = "REQUIRED";

  private static final String CODE_INVALID_FORMAT = "INVALID_FORMAT";

  private static final String PAGE_KEY = "personalInformation";
  private static final String SOURCE_SERVER = "SERVER";

  private final BffPagesProperties properties;

  public PersonalInformationUpdateErrorMapper(BffPagesProperties properties) {
    this.properties = properties;
  }

  public record MappedError(String type, String code, List<String> targets) {
    public MappedError {
      targets = List.copyOf(targets);
    }
  }

  // Kept separate from the legacy response path until the result contract is migrated.
  public List<MappedError> toMappedErrors(UpdatePersonalInformationResult account) {
    Objects.requireNonNull(account, "account");
    if (account.success()) {
      return List.of();
    }
    if (account.errors().isEmpty()) {
      if (account.selected()) {
        throw new IllegalStateException("Selected account update errors are missing.");
      }
      log.warn("event=personal-information-update-other-account-errors-missing");
      return List.of(
          new MappedError(null, "personalInformation.update.rejected", List.of()));
    }
    Map<String, String> bindings = frontendFieldByApimField();
    return account.errors().stream()
        .map(
            error ->
                new MappedError(
                    error.type(), mappedCode(error.code()), mappedTargets(error.fields(), bindings)))
        .toList();
  }

  private String mappedCode(String downstreamCode) {
    if (downstreamCode == null) {
      return "personalInformation.update.rejected";
    }
    return switch (downstreamCode) {
      case "REQUIRED" -> "personalInformation.field.required";
      case "INVALID_FORMAT" -> "personalInformation.field.invalidFormat";
      case "AT_LEAST_ONE_REQUIRED" -> "personalInformation.address.atLeastOneRequired";
      default -> "personalInformation.update.rejected";
    };
  }

  private List<String> mappedTargets(List<String> fields, Map<String, String> bindings) {
    return fields.stream()
        .flatMap(field -> Arrays.stream(field.split("\\|")))
        .map(String::trim)
        .filter(field -> !field.isEmpty())
        .map(
            field -> {
              String target = bindings.get(field);
              if (target == null) {
                log.warn("event=personal-information-update-unknown-field targetOmitted=true");
              }
              return target;
            })
        .filter(Objects::nonNull)
        .distinct()
        .toList();
  }

  public List<ApiError> toApiErrors(List<UpdatePersonalInformationError> rawErrors) {
    if (rawErrors == null || rawErrors.isEmpty()) {
      return List.of(
          ApiError.business(
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
      result.add(toApiError(raw, frontendFieldByApimField));
    }
    return List.copyOf(result);
  }

  private ApiError toApiError(
      UpdatePersonalInformationError raw, Map<String, String> frontendFieldByApimField) {
    List<String> targets = toTargets(raw.fields(), frontendFieldByApimField);
    String type = normalizeType(raw.type());
    String code = resolveCode(raw.code(), targets, type);
    String message = resolveMessage(code, raw.code(), targets, type);
    return new ApiError(type, code, message, targets, "ERROR", SOURCE_SERVER);
  }

  private List<String> toTargets(
      List<String> rawFields, Map<String, String> frontendFieldByApimField) {
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
    PageSchemaProperties page =
        properties == null || properties.getPages() == null
            ? null
            : properties.getPages().get(PAGE_KEY);
    if (page == null || page.getForm() == null || page.getForm().getSections() == null) {
      return result;
    }
    for (SectionProperties section : page.getForm().getSections()) {
      collectFieldBindings(section, result);
    }
    return result;
  }

  private void collectFieldBindings(SectionProperties section, Map<String, String> result) {
    if (section == null || section.getFields() == null) {
      return;
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
    String fromYaml = yamlCodeIfPresent(targets, normalized);
    if (fromYaml != null) {
      return fromYaml;
    }
    String field = targets.isEmpty() ? "field" : targets.get(0);
    return switch (normalized) {
      case CODE_REQUIRED -> "personalInformation." + toCodeToken(field) + ".required";
      case CODE_INVALID_FORMAT -> "personalInformation." + toCodeToken(field) + ".invalid";
      default ->
          rawCode == null || rawCode.isBlank()
              ? "personalInformation.update.validationFailed"
              : rawCode.trim();
    };
  }

  private String yamlCodeIfPresent(List<String> targets, String normalized) {
    if (targets.isEmpty()) {
      return null;
    }
    return findValidationCode(targets.get(0), normalized);
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
      if ((CODE_REQUIRED.equals(normalizedBackendCode) && CODE_REQUIRED.equals(type))
          || (CODE_INVALID_FORMAT.equals(normalizedBackendCode)
              && ("EMAIL".equals(type) || "PATTERN".equals(type)))) {
        return rule.getCode();
      }
    }
    return null;
  }

  private String resolveMessage(
      String resolvedCode, String rawCode, List<String> targets, String type) {
    if ("personalInformation.address.atLeastOneRequired".equals(resolvedCode)) {
      return "Please input a residential address or correspondence address.";
    }
    String fromYaml = yamlMessageIfPresent(targets, rawCode);
    if (fromYaml != null && !fromYaml.isBlank()) {
      return fromYaml;
    }
    String normalized = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case CODE_REQUIRED -> "This field is required.";
      case CODE_INVALID_FORMAT -> "Please input a valid value.";
      default -> "The submitted information is invalid.";
    };
  }

  private String yamlMessageIfPresent(List<String> targets, String rawCode) {
    if (targets.isEmpty()) {
      return null;
    }
    return findValidationMessage(targets.get(0), rawCode);
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
      if (matchesBackendCode(normalizedBackendCode, rule.getType())) {
        String en = rule.getMessage().get("en");
        return en != null ? en : rule.getMessage().values().stream().findFirst().orElse(null);
      }
    }
    return null;
  }

  private boolean matchesBackendCode(String normalizedBackendCode, String rawType) {
    String type = rawType == null ? "" : rawType.trim().toUpperCase(Locale.ROOT);
    return (CODE_REQUIRED.equals(normalizedBackendCode) && CODE_REQUIRED.equals(type))
        || (CODE_INVALID_FORMAT.equals(normalizedBackendCode)
            && ("EMAIL".equals(type) || "PATTERN".equals(type)));
  }

  private FieldProperties findField(String frontendField) {
    PageSchemaProperties page =
        properties == null || properties.getPages() == null
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

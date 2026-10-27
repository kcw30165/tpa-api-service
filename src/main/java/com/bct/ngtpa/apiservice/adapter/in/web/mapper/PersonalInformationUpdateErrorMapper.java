package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ApimBindingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.PageSchemaProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.SectionProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ValidationRuleProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MappedError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PersonalInformationUpdateErrorMapper {
  private static final Logger log =
      LoggerFactory.getLogger(PersonalInformationUpdateErrorMapper.class);
  private static final String PAGE_KEY = "personalInformation";
  private static final String INVALID_FORMAT_FALLBACK = "personalInformation.field.invalidFormat";
  private final BffPagesProperties properties;

  public PersonalInformationUpdateErrorMapper(BffPagesProperties properties) {
    this.properties = properties;
  }

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
      return List.of(new MappedError(null, "personalInformation.update.rejected", List.of()));
    }
    List<FieldProperties> fields = configuredFields();
    Map<String, String> bindings = frontendFieldByApimField(fields);
    return account.errors().stream()
        .map(
            error -> {
              List<String> targets = mappedTargets(error.fields(), bindings);
              return new MappedError(
                  error.type(), mappedCode(error.code(), targets, fields), targets);
            })
        .toList();
  }

  private String mappedCode(
      String downstreamCode, List<String> targets, List<FieldProperties> fields) {
    if (downstreamCode == null) {
      return "personalInformation.update.rejected";
    }
    return switch (downstreamCode) {
      case "REQUIRED" -> "personalInformation.field.required";
      case "INVALID_FORMAT" -> invalidFormatCode(targets, fields);
      case "AT_LEAST_ONE_REQUIRED" -> "personalInformation.address.atLeastOneRequired";
      default -> "personalInformation.update.rejected";
    };
  }

  private String invalidFormatCode(List<String> targets, List<FieldProperties> fields) {
    if (targets.isEmpty()) {
      return INVALID_FORMAT_FALLBACK;
    }
    String commonCode = null;
    for (String target : targets) {
      FieldProperties field =
          fields.stream().filter(value -> target.equals(value.getId())).findFirst().orElse(null);
      if (field == null || field.getValidations() == null) {
        return INVALID_FORMAT_FALLBACK;
      }
      List<String> codes =
          field.getValidations().stream()
              .filter(Objects::nonNull)
              .map(ValidationRuleProperties::getCode)
              .filter(code -> code != null && code.endsWith(".invalid"))
              .distinct()
              .toList();
      if (codes.size() != 1 || (commonCode != null && !commonCode.equals(codes.getFirst()))) {
        return INVALID_FORMAT_FALLBACK;
      }
      commonCode = codes.getFirst();
    }
    return commonCode;
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

  private List<FieldProperties> configuredFields() {
    PageSchemaProperties page =
        properties == null || properties.getPages() == null
            ? null
            : properties.getPages().get(PAGE_KEY);
    if (page == null || page.getForm() == null || page.getForm().getSections() == null) {
      return List.of();
    }
    return page.getForm().getSections().stream()
        .filter(Objects::nonNull)
        .map(SectionProperties::getFields)
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .filter(field -> field != null && field.getId() != null && !field.getId().isBlank())
        .toList();
  }

  private Map<String, String> frontendFieldByApimField(List<FieldProperties> fields) {
    Map<String, String> result = new LinkedHashMap<>();
    for (FieldProperties field : fields) {
      ApimBindingProperties binding = field.getApimBinding();
      if (binding == null) {
        continue;
      }
      putReverse(result, binding.getData(), field.getId());
      putReverse(result, binding.getConfig(), field.getId());
    }
    return result;
  }

  private void putReverse(Map<String, String> result, String apimField, String frontendField) {
    if (apimField != null && !apimField.isBlank()) {
      result.putIfAbsent(apimField.trim(), frontendField);
    }
  }
}

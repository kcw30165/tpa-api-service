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
  private static final String UPDATE_REJECTED = "personalInformation.update.rejected";
  private static final Map<String, String> CONDITIONAL_REQUIRED_CODES =
      Map.of(
          "MISSING_OVERSEAS_COUNTRY",
          "personalInformation.overseasCountryCode.requiredWhenPhonePresent",
          "MISSING_OVERSEAS_PHONE",
          "personalInformation.overseasPhoneNumber.requiredWhenCodePresent",
          "MISSING_BUSSINESS_PHONE",
          "personalInformation.businessPhone.requiredWhenExtensionPresent");
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
      return List.of(new MappedError(null, UPDATE_REJECTED, List.of()));
    }
    List<FieldProperties> fields = configuredFields();
    Map<String, String> bindings = frontendFieldByApimField(fields);
    return account.errors().stream()
        .map(
            error -> {
              String conditionalCode =
                  error.code() == null ? null : CONDITIONAL_REQUIRED_CODES.get(error.code());
              List<String> targets =
                  conditionalCode == null
                      ? mappedTargets(error.fields(), bindings)
                      : conditionalTargets(conditionalCode, fields);
              return new MappedError(
                  error.type(),
                  conditionalCode == null
                      ? mappedCode(error.code(), targets, fields)
                      : conditionalCode,
                  targets);
            })
        .toList();
  }

  private String mappedCode(
      String downstreamCode, List<String> targets, List<FieldProperties> fields) {
    if (downstreamCode == null) {
      return unknownCode(null);
    }
    return switch (downstreamCode) {
      case "REQUIRED" -> "personalInformation.field.required";
      case "INVALID_FORMAT" -> invalidFormatCode(targets, fields);
      case "AT_LEAST_ONE_REQUIRED" -> "personalInformation.address.atLeastOneRequired";
      case "INVALID_EMAIL" -> "personalInformation.email.invalid";
      case "INVALID_FAX" -> "personalInformation.faxNo.invalid";
      case "INVALID_MOBILE_NUMBER" -> "personalInformation.mobilePhone.invalid";
      case "INVALID_HOME_PHONE" -> "personalInformation.homeTel.invalid";
      case "INVALID_ADDRESS" -> "personalInformation.residentialAddress.invalid";
      case "INVALID_CORR_ADDRESS" -> "personalInformation.mailingAddress.invalid";
      case "BLOCKED_PO_ADDRESS" -> "personalInformation.address.poBoxBlocked";
      default -> unknownCode(downstreamCode);
    };
  }

  private String unknownCode(String downstreamCode) {
    if (log.isWarnEnabled()) {
      String sanitized =
          downstreamCode == null ? "" : downstreamCode.replaceAll("[^A-Za-z0-9_.-]", "");
      log.warn(
          "event=personal-information-update-unknown-downstream-code code={}",
          sanitized.substring(0, Math.min(64, sanitized.length())));
    }
    return UPDATE_REJECTED;
  }

  private List<String> conditionalTargets(String code, List<FieldProperties> fields) {
    PageSchemaProperties page =
        properties == null || properties.getPages() == null
            ? null
            : properties.getPages().get(PAGE_KEY);
    if (page == null || page.getValidations() == null) {
      throw new IllegalStateException(
          "Personal information conditional-required configuration is missing.");
    }
    ValidationRuleProperties rule =
        page.getValidations().stream()
            .filter(Objects::nonNull)
            .filter(
                value ->
                    code.equals(value.getCode()) && "conditionalRequired".equals(value.getType()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Personal information conditional-required rule is missing."));
    if (rule.getThen() == null
        || rule.getThen().getTargets() == null
        || rule.getThen().getTargets().isEmpty()) {
      throw new IllegalStateException(
          "Personal information conditional-required targets are missing.");
    }
    List<String> targets = rule.getThen().getTargets();
    if (targets.stream()
        .anyMatch(
            target ->
                target == null
                    || fields.stream().noneMatch(field -> target.equals(field.getId())))) {
      throw new IllegalStateException(
          "Personal information conditional-required target is unknown.");
    }
    return targets.stream().distinct().toList();
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

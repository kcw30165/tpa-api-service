package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.ApimBindingProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.UpdatePersonalInformationResponse;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PersonalInformationUpdateResponseMapper {

    private static final String PAGE_KEY = "personalInformation";
    private static final String SOURCE_SERVER = "SERVER";

    private final BffPagesProperties properties;

    public PersonalInformationUpdateResponseMapper(BffPagesProperties properties) {
        this.properties = properties;
    }

    public UpdatePersonalInformationResponse toResponse(UpdatePersonalInformationResult result) {
        if (result != null && result.success()) {
            return UpdatePersonalInformationResponse.updated(new PersonalInformationUpdateResultResponse(
                    result.refNo(),
                    result.submitDate(),
                    result.submitTime()));
        }

        List<ApiError> errors = mapErrors(result == null ? List.of() : result.errors());
        if (errors.isEmpty()) {
            errors = List.of(ApiError.business(
                    "personalInformation.update.rejected",
                    "Your personal information update was not accepted.",
                    List.of(),
                    SOURCE_SERVER));
            return UpdatePersonalInformationResponse.businessRejected(errors);
        }
        return UpdatePersonalInformationResponse.validationFailed(errors);
    }

    private List<ApiError> mapErrors(List<UpdatePersonalInformationError> rawErrors) {
        if (rawErrors == null || rawErrors.isEmpty()) {
            return List.of();
        }
        Map<String, String> fieldByApimName = buildFieldByApimName();
        List<ApiError> errors = new ArrayList<>();
        for (UpdatePersonalInformationError raw : rawErrors) {
            if (raw == null) {
                continue;
            }
            String type = normalizeType(raw.type());
            String code = normalizeCode(type, raw.code());
            List<String> targets = mapTargets(raw.fields(), fieldByApimName);
            String message = resolveMessage(code);
            errors.add(new ApiError(type, code, message, targets, "ERROR", SOURCE_SERVER));
        }
        return List.copyOf(errors);
    }

    private List<String> mapTargets(List<String> rawFields, Map<String, String> fieldByApimName) {
        if (rawFields == null || rawFields.isEmpty()) {
            return List.of();
        }
        List<String> targets = new ArrayList<>();
        for (String rawField : rawFields) {
            if (rawField == null || rawField.isBlank()) {
                continue;
            }
            Arrays.stream(rawField.split("\\|"))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(value -> fieldByApimName.getOrDefault(value, value))
                    .forEach(targets::add);
        }
        return List.copyOf(targets);
    }

    private Map<String, String> buildFieldByApimName() {
        Map<String, String> fieldByApimName = new LinkedHashMap<>();
        var page = properties.getPages().get(PAGE_KEY);
        if (page == null || page.getForm() == null || page.getForm().getSections() == null) {
            return fieldByApimName;
        }
        for (var section : page.getForm().getSections()) {
            if (section == null || section.getFields() == null) {
                continue;
            }
            for (FieldProperties field : section.getFields()) {
                if (field == null || field.getId() == null) {
                    continue;
                }
                ApimBindingProperties binding = field.getApimBinding();
                if (binding == null) {
                    continue;
                }
                putIfPresent(fieldByApimName, binding.getData(), field.getId());
                putIfPresent(fieldByApimName, binding.getConfig(), field.getId());
            }
        }
        return fieldByApimName;
    }

    private void putIfPresent(Map<String, String> mapping, String key, String fieldId) {
        if (key != null && !key.isBlank()) {
            mapping.putIfAbsent(key.trim(), fieldId);
        }
    }

    private String normalizeType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "FORM";
        }
        String normalized = rawType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "FIELD", "CROSS_FIELD", "FORM", "BUSINESS", "SYSTEM" -> normalized;
            default -> "FORM";
        };
    }

    private String normalizeCode(String type, String rawCode) {
        String normalized = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "REQUIRED" -> "personalInformation.field.required";
            case "INVALID_FORMAT" -> "personalInformation.field.invalid";
            case "AT_LEAST_ONE_REQUIRED" -> "personalInformation.address.atLeastOneRequired";
            default -> rawCode == null || rawCode.isBlank()
                    ? "personalInformation.update.validationFailed"
                    : rawCode.trim();
        };
    }

    private String resolveMessage(String code) {
        return switch (code) {
            case "personalInformation.address.atLeastOneRequired" ->
                    "Please input a residential address or correspondence address.";
            case "personalInformation.field.required" ->
                    "This field is required.";
            case "personalInformation.field.invalid" ->
                    "Please input a valid value.";
            default -> "The submitted information is invalid.";
        };
    }
}

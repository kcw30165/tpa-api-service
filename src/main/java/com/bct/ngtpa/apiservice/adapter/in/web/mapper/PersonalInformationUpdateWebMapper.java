package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.BffPagesProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.pageconfig.FieldProperties;
import com.bct.ngtpa.apiservice.adapter.in.web.request.UpdatePersonalInformationRequest;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.exception.InvalidPersonalInformationUpdateException;

import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PersonalInformationUpdateWebMapper {
    
    private static final String PAGE_KEY = "personalInformation";

    private final BffPagesProperties properties;

    public PersonalInformationUpdateWebMapper(BffPagesProperties properties) {
        this.properties = Objects.requireNonNull(properties);
    }

    public UpdatePersonalInformationCommand toCommand(Boolean applyToAllAccounts, UpdatePersonalInformationRequest request) {
        if (request == null || request.fields() == null || request.fields().isEmpty()) {
            throw new InvalidPersonalInformationUpdateException("No personal information fields were submitted.");
        }

        var fieldsById = fieldsById();
        var mapped = new LinkedHashMap<String, Object>();

        request.fields().forEach((fieldId, value) -> {
            var field = fieldsById.get(fieldId);
            if (field == null) {
                log.error("Unknown personal information update field submitted: {}", fieldId);
                throw new InvalidPersonalInformationUpdateException("Unknown personal information field: " + fieldId);
            }

            var binding = field.getApimBinding();
            var dataKey = binding == null ? null : trimToNull(binding.getData());
            if (dataKey == null) {
                log.warn("Personal information update field is UI-only and will be omitted: {}", fieldId);
                return;
            }

            validateSubmittedValue(field, value);
            mapped.put(dataKey, value);
        });

        if (mapped.isEmpty()) {
            throw new InvalidPersonalInformationUpdateException("No updatable personal information fields were submitted.");
        }
        return new UpdatePersonalInformationCommand(applyToAllAccounts, mapped);
    }

    public UpdatePersonalInformationCommand toCommand(
            String ignoredLegacySelectedAccount,
            Boolean applyToAllAccounts,
            UpdatePersonalInformationRequest request) {
        return toCommand(applyToAllAccounts, request);
    }

    private Map<String, FieldProperties> fieldsById() {
        var page = properties.getPages().get(PAGE_KEY);
        if (page == null || page.getForm() == null || page.getForm().getSections() == null) {
            throw new IllegalStateException("Personal information page configuration is missing.");
        }
        var result = new LinkedHashMap<String, FieldProperties>();
        for (var section : page.getForm().getSections()) {
            if (section == null || section.getFields() == null) {
                continue;
            }
            for (var field : section.getFields()) {
                if (field != null && trimToNull(field.getId()) != null) {
                    result.put(field.getId(), field);
                }
            }
        }
        return result;
    }

    private void validateSubmittedValue(FieldProperties field, Object value) {
        var fieldId = field.getId();
        if (isRequired(field) && isBlankValue(value)) {
            throw new InvalidPersonalInformationUpdateException("Personal information field is required: " + fieldId);
        }
        if (value == null) {
            return;
        }
        if ("boolean".equalsIgnoreCase(field.getDataType()) && !(value instanceof Boolean)) {
            throw new InvalidPersonalInformationUpdateException("Personal information field must be boolean: " + fieldId);
        }
        if (isTextLike(field) && !(value instanceof String)) {
            throw new InvalidPersonalInformationUpdateException("Personal information field must be string: " + fieldId);
        }
        if (value instanceof String text) {
            if (field.getMaxLength() != null && text.length() > field.getMaxLength()) {
                throw new InvalidPersonalInformationUpdateException("Personal information field exceeds maxLength: " + fieldId);
            }
            var pattern = trimToNull(field.getPattern());
            if (pattern != null && !text.isBlank() && !Pattern.compile(pattern).matcher(text).matches()) {
                throw new InvalidPersonalInformationUpdateException("Personal information field pattern mismatch: " + fieldId);
            }
            if ("email".equalsIgnoreCase(field.getControlType()) && !text.isBlank()
                    && !Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matcher(text).matches()) {
                throw new InvalidPersonalInformationUpdateException("Personal information field must be email: " + fieldId);
            }
        }
    }

    private boolean isRequired(FieldProperties field) {
        if (field.getValidations() == null) {
            return false;
        }
        return field.getValidations().stream()
                .filter(Objects::nonNull)
                .anyMatch(rule -> "required".equalsIgnoreCase(rule.getType()));
    }

    private boolean isTextLike(FieldProperties field) {
        var dataType = field.getDataType();
        return dataType == null || dataType.isBlank() || "string".equalsIgnoreCase(dataType);
    }

    private boolean isBlankValue(Object value) {
        return value == null || (value instanceof String text && text.trim().isEmpty());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

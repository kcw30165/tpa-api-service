package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiMessage;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PersonalInformationUpdateResponseMapper {
    private static final String SUCCESS_CODE = "personalInformation.update.success";
    private static final String SUCCESS_MESSAGE = "Your personal information has been updated successfully.";
    private static final String PARTIAL_SUCCESS_CODE = "personalInformation.update.partialSuccess";

    private final PersonalInformationUpdateErrorMapper errorMapper;

    public PersonalInformationUpdateResponseMapper(PersonalInformationUpdateErrorMapper errorMapper) {
        this.errorMapper = errorMapper;
    }

    public MutationResponse<List<PersonalInformationUpdateResultResponse>> toResponse(List<UpdatePersonalInformationResult> results) {
        if (results == null || results.isEmpty()) {
            return MutationResponse.failure(ApiStatus.SYSTEM_ERROR, List.of());
        }
        UpdatePersonalInformationResult selected = selectedResult(results);
        List<PersonalInformationUpdateResultResponse> resultResponses = resultResponses(results);
        if (selected.success()) {
            if (hasAnyFailure(results)) {
                return MutationResponse.success(ApiStatus.PARTIAL_SUCCESS, resultResponses,
                        List.of(ApiMessage.warning(PARTIAL_SUCCESS_CODE, partialSuccessMessage(results), "PAGE")));
            }
            return MutationResponse.success(ApiStatus.UPDATED, resultResponses,
                    List.of(ApiMessage.success(SUCCESS_CODE, SUCCESS_MESSAGE, "PAGE")));
        }
        List<ApiError> selectedErrors = errorMapper.toApiErrors(selected.errors());
        return MutationResponse.failure(failureStatus(results, selected.errors()), resultResponses, selectedErrors);
    }

    public MutationResponse<List<PersonalInformationUpdateResultResponse>> toResponse(UpdatePersonalInformationResult result) {
        return toResponse(result == null ? List.of() : List.of(result));
    }

    private List<PersonalInformationUpdateResultResponse> resultResponses(List<UpdatePersonalInformationResult> results) {
        return results.stream()
                .map(result -> new PersonalInformationUpdateResultResponse(
                        result.selected(), result.success(), result.policyNo(), result.certNo(), result.env(),
                        result.refNo(), result.submitDate(), result.submitTime(),
                        result.errors().isEmpty() ? List.of() : errorMapper.toApiErrors(result.errors())))
                .toList();
    }

    private UpdatePersonalInformationResult selectedResult(List<UpdatePersonalInformationResult> results) {
        return results.stream()
                .filter(UpdatePersonalInformationResult::selected)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Personal information update result does not contain selected account."));
    }

    private ApiStatus failureStatus(List<UpdatePersonalInformationResult> results, List<UpdatePersonalInformationError> selectedErrors) {
        if (hasAnySuccess(results) && hasAnyFailure(results)) {
            return ApiStatus.PARTIAL_SUCCESS;
        }
        return selectedErrors.stream()
                .map(UpdatePersonalInformationError::type)
                .findFirst()
                .map(type -> switch (type) {
                    case "DOWNSTREAM_ERROR" -> ApiStatus.DOWNSTREAM_ERROR;
                    case "DOWNSTREAM_REJECTED" -> ApiStatus.DOWNSTREAM_REJECTED;
                    default -> ApiStatus.VALIDATION_FAILED;
                })
                .orElse(ApiStatus.SYSTEM_ERROR);
    }

    private boolean hasAnySuccess(List<UpdatePersonalInformationResult> results) {
        return results.stream().anyMatch(UpdatePersonalInformationResult::success);
    }

    private boolean hasAnyFailure(List<UpdatePersonalInformationResult> results) {
        return results.stream().anyMatch(result -> !result.success());
    }

    private String partialSuccessMessage(List<UpdatePersonalInformationResult> results) {
        var otherSuccesses = results.stream().filter(result -> !result.selected() && result.success()).map(this::accountLabel).toList();
        var otherFailures = results.stream().filter(result -> !result.selected() && !result.success()).map(this::failedAccountMessage).toList();
        StringBuilder message = new StringBuilder("The update was successful for the selected account");
        if (!otherSuccesses.isEmpty()) {
            message.append(" and ").append(joinLabels(otherSuccesses));
        }
        message.append(", but failed for ").append(joinLabels(otherFailures)).append(".");
        return message.toString();
    }

    private String failedAccountMessage(UpdatePersonalInformationResult result) {
        var details = result.errors().isEmpty() ? "" : errorMapper.toApiErrors(result.errors()).stream()
                .map(ApiError::message)
                .filter(message -> message != null && !message.isBlank())
                .collect(Collectors.joining("; "));
        return details.isBlank() ? accountLabel(result) : accountLabel(result) + ": " + details;
    }

    private String accountLabel(UpdatePersonalInformationResult result) {
        String policy = result.policyNo() == null || result.policyNo().isBlank() ? "unknown policy" : "policy " + result.policyNo();
        String cert = result.certNo() == null || result.certNo().isBlank() ? "unknown certificate" : "certificate " + result.certNo();
        return policy + " " + cert;
    }

    private String joinLabels(List<String> labels) {
        if (labels.isEmpty()) return "";
        if (labels.size() == 1) return labels.getFirst();
        return String.join(", ", labels.subList(0, labels.size() - 1)) + " and " + labels.getLast();
    }
}

package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiMessage;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateAccountResponse;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationAccountResult;
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

    public MutationResponse<List<PersonalInformationUpdateAccountResponse>> toResponse(UpdatePersonalInformationResult result) {
        if (result != null && result.success()) {
            if (hasOtherAccountFailures(result)) {
                return MutationResponse.success(
                        ApiStatus.PARTIAL_SUCCESS,
                        accountResponses(result),
                        List.of(ApiMessage.warning(PARTIAL_SUCCESS_CODE, partialSuccessMessage(result), "PAGE")));
            }
            return MutationResponse.success(
                    ApiStatus.UPDATED,
                    accountResponses(result),
                    List.of(ApiMessage.success(SUCCESS_CODE, SUCCESS_MESSAGE, "PAGE")));
        }

        List<UpdatePersonalInformationError> errors = selectedAccountErrors(result);
        List<ApiError> apiErrors = errorMapper.toApiErrors(errors);
        ApiStatus status = failureStatus(result, errors);
        if (hasAccountResults(result)) {
            return MutationResponse.failure(status, accountResponses(result), apiErrors);
        }
        return MutationResponse.failure(status, apiErrors);
    }

    private List<PersonalInformationUpdateAccountResponse> accountResponses(UpdatePersonalInformationResult result) {
        if (!hasAccountResults(result)) {
            return List.of();
        }
        return result.accountResults().stream()
                .map(account -> new PersonalInformationUpdateAccountResponse(
                        account.selected(),
                        account.success(),
                        account.policyNo(),
                        account.certNo(),
                        account.env(),
                        account.refNo(),
                        account.submitDate(),
                        account.submitTime(),
                        account.errors().isEmpty() ? List.of() : errorMapper.toApiErrors(account.errors())))
                .toList();
    }

    private ApiStatus failureStatus(UpdatePersonalInformationResult result, List<UpdatePersonalInformationError> errors) {
        if (hasAccountResults(result) && hasAnyAccountSuccess(result) && hasAnyAccountFailure(result)) {
            return ApiStatus.PARTIAL_SUCCESS;
        }
        return errors.stream()
                .map(UpdatePersonalInformationError::type)
                .findFirst()
                .map(type -> switch (type) {
                    case "DOWNSTREAM_ERROR" -> ApiStatus.DOWNSTREAM_ERROR;
                    case "DOWNSTREAM_REJECTED" -> ApiStatus.DOWNSTREAM_REJECTED;
                    default -> ApiStatus.VALIDATION_FAILED;
                })
                .orElse(ApiStatus.SYSTEM_ERROR);
    }

    private boolean hasAccountResults(UpdatePersonalInformationResult result) {
        return result != null && !result.accountResults().isEmpty();
    }

    private boolean hasAnyAccountSuccess(UpdatePersonalInformationResult result) {
        return hasAccountResults(result) && result.accountResults().stream().anyMatch(UpdatePersonalInformationAccountResult::success);
    }

    private boolean hasAnyAccountFailure(UpdatePersonalInformationResult result) {
        return hasAccountResults(result) && result.accountResults().stream().anyMatch(account -> !account.success());
    }

    private boolean hasOtherAccountFailures(UpdatePersonalInformationResult result) {
        return result.accountResults().stream().anyMatch(account -> !account.selected() && !account.success());
    }

    private List<UpdatePersonalInformationError> selectedAccountErrors(UpdatePersonalInformationResult result) {
        if (result == null) {
            return List.of();
        }
        return result.accountResults().stream()
                .filter(UpdatePersonalInformationAccountResult::selected)
                .findFirst()
                .map(UpdatePersonalInformationAccountResult::errors)
                .filter(errors -> !errors.isEmpty())
                .orElse(result.errors());
    }

    private String partialSuccessMessage(UpdatePersonalInformationResult result) {
        var otherSuccesses = result.accountResults().stream()
                .filter(account -> !account.selected() && account.success())
                .map(this::accountLabel)
                .toList();
        var otherFailures = result.accountResults().stream()
                .filter(account -> !account.selected() && !account.success())
                .map(this::failedAccountMessage)
                .toList();

        StringBuilder message = new StringBuilder("The update was successful for the selected account");
        if (!otherSuccesses.isEmpty()) {
            message.append(" and ").append(joinLabels(otherSuccesses));
        }
        message.append(", but failed for ").append(joinLabels(otherFailures)).append(".");
        return message.toString();
    }

    private String failedAccountMessage(UpdatePersonalInformationAccountResult account) {
        var details = account.errors().isEmpty()
                ? ""
                : errorMapper.toApiErrors(account.errors()).stream()
                        .map(ApiError::message)
                        .filter(message -> message != null && !message.isBlank())
                        .collect(Collectors.joining("; "));
        return details.isBlank() ? accountLabel(account) : accountLabel(account) + ": " + details;
    }

    private String accountLabel(UpdatePersonalInformationAccountResult account) {
        String policy = account.policyNo() == null || account.policyNo().isBlank()
                ? "unknown policy" : "policy " + account.policyNo();
        String cert = account.certNo() == null || account.certNo().isBlank()
                ? "unknown certificate" : "certificate " + account.certNo();
        return policy + " " + cert;
    }

    private String joinLabels(List<String> labels) {
        if (labels.isEmpty()) {
            return "";
        }
        if (labels.size() == 1) {
            return labels.getFirst();
        }
        return String.join(", ", labels.subList(0, labels.size() - 1)) + " and " + labels.getLast();
    }
}

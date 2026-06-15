package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.List;

/**
 * PUT personal-information response: base envelope + endpoint-specific result.
 */
public record UpdatePersonalInformationResponse(
        boolean success,
        String status,
        List<PersonalInformationUpdateAccountResponse> result,
        List<ApiMessage> messages,
        List<ApiError> errors) {

    public UpdatePersonalInformationResponse {
        messages = messages == null ? List.of() : List.copyOf(messages);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static UpdatePersonalInformationResponse updated(List<PersonalInformationUpdateAccountResponse> result) {
        return new UpdatePersonalInformationResponse(
                true,
                ApiStatus.UPDATED.toString(),
                result,
                List.of(ApiMessage.success(
                        "personalInformation.update.success",
                        "Your personal information has been updated successfully.",
                        "PAGE")),
                List.of());
    }

    public static UpdatePersonalInformationResponse validationFailed(List<ApiError> errors) {
        return new UpdatePersonalInformationResponse(false, ApiStatus.VALIDATION_FAILED.toString(), null, List.of(), errors);
    }

    public static UpdatePersonalInformationResponse businessRejected(List<ApiError> errors) {
        return new UpdatePersonalInformationResponse(false, ApiStatus.BUSINESS_REJECTED.toString(), null, List.of(), errors);
    }

    public static UpdatePersonalInformationResponse systemError(List<ApiError> errors) {
        return new UpdatePersonalInformationResponse(false, ApiStatus.SYSTEM_ERROR.toString(), null, List.of(), errors);
    }
}

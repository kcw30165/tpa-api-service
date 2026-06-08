package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiMessage;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PersonalInformationUpdateResponseMapper {

    private static final String SUCCESS_CODE = "personalInformation.update.success";
    private static final String SUCCESS_MESSAGE = "Your personal information has been updated successfully.";

    private final PersonalInformationUpdateErrorMapper errorMapper;

    public PersonalInformationUpdateResponseMapper(PersonalInformationUpdateErrorMapper errorMapper) {
        this.errorMapper = errorMapper;
    }

    public MutationResponse<PersonalInformationUpdateResultResponse> toResponse(
            UpdatePersonalInformationResult result) {
        if (result != null && result.success()) {
            return MutationResponse.success(
                    ApiStatus.UPDATED,
                    new PersonalInformationUpdateResultResponse(
                            result.refNo(),
                            result.submitDate(),
                            result.submitTime()),
                    List.of(ApiMessage.success(SUCCESS_CODE, SUCCESS_MESSAGE, "PAGE")));
        }
        List<UpdatePersonalInformationError> errors = result == null ? List.of() : result.errors();

        // Map the internal error type string directly to the definitive ApiStatus enum
        ApiStatus status = errors.stream()
                .map(UpdatePersonalInformationError::type)
                .findFirst()
                .map(type -> switch (type) {
                    case "DOWNSTREAM_ERROR" -> ApiStatus.DOWNSTREAM_ERROR;
                    case "DOWNSTREAM_REJECTED" -> ApiStatus.DOWNSTREAM_REJECTED;
                    default -> ApiStatus.VALIDATION_FAILED; // Fallback for local Java validation
                })
                .orElse(ApiStatus.SYSTEM_ERROR);

        return MutationResponse.failure(
                status,
                errorMapper.toApiErrors(errors));
    }
}

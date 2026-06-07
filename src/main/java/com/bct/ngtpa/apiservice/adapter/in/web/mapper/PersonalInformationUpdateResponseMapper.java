package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiMessage;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse;
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

    public MutationResponse<PersonalInformationUpdateResultResponse> toResponse(UpdatePersonalInformationResult result) {
        if (result != null && result.success()) {
            return MutationResponse.success(
                    ApiStatus.UPDATED,
                    new PersonalInformationUpdateResultResponse(
                            result.refNo(),
                            result.submitDate(),
                            result.submitTime()),
                    List.of(ApiMessage.success(SUCCESS_CODE, SUCCESS_MESSAGE, "PAGE")));
        }

        return MutationResponse.failure(
                ApiStatus.VALIDATION_FAILED,
                errorMapper.toApiErrors(result == null ? List.of() : result.errors()));
    }
}

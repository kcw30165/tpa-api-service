package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalInformationUpdateResponseMapperTest {

    @Test
    void mapsApimSuccessToMutationUpdatedResponse() {
        PersonalInformationUpdateErrorMapper errorMapper = mock(PersonalInformationUpdateErrorMapper.class);
        PersonalInformationUpdateResponseMapper mapper = new PersonalInformationUpdateResponseMapper(errorMapper);

        MutationResponse<PersonalInformationUpdateResultResponse> response = mapper.toResponse(
                new UpdatePersonalInformationResult(true, "990000001", "2026-06-07", "08:36:51"));

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo(ApiStatus.UPDATED);
        assertThat(response.result().refNo()).isEqualTo("990000001");
        assertThat(response.result().submitDate()).isEqualTo("2026-06-07");
        assertThat(response.result().submitTime()).isEqualTo("08:36:51");
        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().get(0).type()).isEqualTo("SUCCESS");
        assertThat(response.errors()).isEmpty();
    }

    @Test
    void mapsApimValidationFailureToMutationValidationFailedResponse() {
        PersonalInformationUpdateErrorMapper errorMapper = mock(PersonalInformationUpdateErrorMapper.class);
        ApiError apiError = ApiError.field(
                "personalInformation.email.required",
                "Please provide a valid email address.",
                List.of("emailAddress"),
                "SERVER");
        when(errorMapper.toApiErrors(List.of(new UpdatePersonalInformationError("FIELD", List.of("email"), "REQUIRED"))))
                .thenReturn(List.of(apiError));
        PersonalInformationUpdateResponseMapper mapper = new PersonalInformationUpdateResponseMapper(errorMapper);

        MutationResponse<PersonalInformationUpdateResultResponse> response = mapper.toResponse(
                new UpdatePersonalInformationResult(
                        false,
                        null,
                        null,
                        null,
                        List.of(new UpdatePersonalInformationError("FIELD", List.of("email"), "REQUIRED"))));

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
        assertThat(response.result()).isNull();
        assertThat(response.messages()).isEmpty();
        assertThat(response.errors()).containsExactly(apiError);
    }
}

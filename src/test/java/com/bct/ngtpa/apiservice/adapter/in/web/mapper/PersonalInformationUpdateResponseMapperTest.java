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
    void mapsSingleSelectedSuccessToUpdatedResultArray() {
        PersonalInformationUpdateErrorMapper errorMapper = mock(PersonalInformationUpdateErrorMapper.class);
        PersonalInformationUpdateResponseMapper mapper = new PersonalInformationUpdateResponseMapper(errorMapper);

        MutationResponse<List<PersonalInformationUpdateResultResponse>> response = mapper.toResponse(List.of(
                new UpdatePersonalInformationResult(
                        true,
                        true,
                        "00000000118",
                        "2",
                        "DB",
                        "990000001",
                        "2026-06-07",
                        "08:36:51",
                        List.of())));

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo(ApiStatus.UPDATED);
        assertThat(response.result()).hasSize(1);
        assertThat(response.result().getFirst().selected()).isTrue();
        assertThat(response.result().getFirst().success()).isTrue();
        assertThat(response.result().getFirst().refNo()).isEqualTo("990000001");
        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().getFirst().type()).isEqualTo("SUCCESS");
        assertThat(response.errors()).isEmpty();
    }

    @Test
    void mapsSingleSelectedValidationFailureToBlockingErrorsAndResultArray() {
        PersonalInformationUpdateErrorMapper errorMapper = mock(PersonalInformationUpdateErrorMapper.class);
        var selectedError = new UpdatePersonalInformationError("FIELD", List.of("email"), "REQUIRED");
        ApiError apiError = ApiError.field(
                "personalInformation.email.required",
                "email is required",
                List.of("emailAddress"),
                "SERVER");
        when(errorMapper.toApiErrors(List.of(selectedError))).thenReturn(List.of(apiError));
        PersonalInformationUpdateResponseMapper mapper = new PersonalInformationUpdateResponseMapper(errorMapper);

        MutationResponse<List<PersonalInformationUpdateResultResponse>> response = mapper.toResponse(List.of(
                new UpdatePersonalInformationResult(
                        true,
                        false,
                        "00000000118",
                        "3",
                        "DB",
                        null,
                        null,
                        null,
                        List.of(selectedError))));

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
        assertThat(response.result()).hasSize(1);
        assertThat(response.result().getFirst().selected()).isTrue();
        assertThat(response.result().getFirst().success()).isFalse();
        assertThat(response.result().getFirst().errors()).containsExactly(apiError);
        assertThat(response.messages()).isEmpty();
        assertThat(response.errors()).containsExactly(apiError);
    }

    @Test
    void mapsSelectedSuccessAndOtherAccountFailureToPartialSuccessAccountArray() {
        PersonalInformationUpdateErrorMapper errorMapper = mock(PersonalInformationUpdateErrorMapper.class);
        var failedError = new UpdatePersonalInformationError("FIELD", List.of("email"), "REQUIRED");
        ApiError mappedFailedError = ApiError.field(
                "personalInformation.email.required",
                "email is required",
                List.of("emailAddress"),
                "SERVER");
        when(errorMapper.toApiErrors(List.of(failedError))).thenReturn(List.of(mappedFailedError));
        PersonalInformationUpdateResponseMapper mapper = new PersonalInformationUpdateResponseMapper(errorMapper);

        MutationResponse<List<PersonalInformationUpdateResultResponse>> response = mapper.toResponse(List.of(
                new UpdatePersonalInformationResult(
                        true,
                        true,
                        "00000000118",
                        "2",
                        "DB",
                        "260001373",
                        "2025-12-31",
                        "15:42:52",
                        List.of()),
                new UpdatePersonalInformationResult(
                        false,
                        false,
                        "00000000118",
                        "3",
                        "DB",
                        "260001374",
                        "2025-12-31",
                        "15:42:52",
                        List.of(failedError))));

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo(ApiStatus.PARTIAL_SUCCESS);
        assertThat(response.result()).hasSize(2);
        assertThat(response.result().get(0).selected()).isTrue();
        assertThat(response.result().get(0).success()).isTrue();
        assertThat(response.result().get(1).selected()).isFalse();
        assertThat(response.result().get(1).success()).isFalse();
        assertThat(response.result().get(1).errors()).containsExactly(mappedFailedError);
        assertThat(response.errors()).isEmpty();
        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().getFirst().type()).isEqualTo("WARNING");
    }

    @Test
    void mapsSelectedAccountFailureWithOtherSuccessToPartialSuccessResultArrayAndTopLevelSelectedErrors() {
        PersonalInformationUpdateErrorMapper errorMapper = mock(PersonalInformationUpdateErrorMapper.class);
        var selectedError = new UpdatePersonalInformationError("FIELD", List.of("email"), "REQUIRED");
        ApiError mappedSelectedError = ApiError.field(
                "personalInformation.email.required",
                "email is required",
                List.of("emailAddress"),
                "SERVER");
        when(errorMapper.toApiErrors(List.of(selectedError))).thenReturn(List.of(mappedSelectedError));
        PersonalInformationUpdateResponseMapper mapper = new PersonalInformationUpdateResponseMapper(errorMapper);

        MutationResponse<List<PersonalInformationUpdateResultResponse>> response = mapper.toResponse(List.of(
                new UpdatePersonalInformationResult(
                        false,
                        true,
                        "00000000118",
                        "2",
                        "DB",
                        "260001373",
                        "2025-12-31",
                        "15:42:52",
                        List.of()),
                new UpdatePersonalInformationResult(
                        true,
                        false,
                        "00000000118",
                        "3",
                        "DB",
                        "260001374",
                        "2025-12-31",
                        "15:42:52",
                        List.of(selectedError))));

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ApiStatus.PARTIAL_SUCCESS);
        assertThat(response.result()).hasSize(2);
        assertThat(response.result().get(0).selected()).isFalse();
        assertThat(response.result().get(0).success()).isTrue();
        assertThat(response.result().get(1).selected()).isTrue();
        assertThat(response.result().get(1).success()).isFalse();
        assertThat(response.result().get(1).errors()).containsExactly(mappedSelectedError);
        assertThat(response.messages()).isEmpty();
        assertThat(response.errors()).containsExactly(mappedSelectedError);
    }
}

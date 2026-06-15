package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateAccountResponse;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationAccountResult;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalInformationUpdateResponseMapperTest {

    @Test
    void mapsSelectedAccountSuccessToUpdatedWithAccountArrayResult() {
        PersonalInformationUpdateErrorMapper errorMapper = mock(PersonalInformationUpdateErrorMapper.class);
        PersonalInformationUpdateResponseMapper mapper = new PersonalInformationUpdateResponseMapper(errorMapper);

        MutationResponse<List<PersonalInformationUpdateAccountResponse>> response = mapper.toResponse(
                new UpdatePersonalInformationResult(true, "990000001", "2026-06-07", "08:36:51", List.of(),
                        List.of(new UpdatePersonalInformationAccountResult(
                                true, true, "00000000118", "2", "DB", "990000001", "2026-06-07", "08:36:51", List.of()))));

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo(ApiStatus.UPDATED);
        assertThat(response.result()).hasSize(1);
        assertThat(response.result().getFirst().selected()).isTrue();
        assertThat(response.result().getFirst().refNo()).isEqualTo("990000001");
        assertThat(response.messages()).hasSize(1);
        assertThat(response.errors()).isEmpty();
    }

    @Test
    void mapsValidationFailureWithoutAccountResultsToBlockingErrorsAndNullResult() {
        PersonalInformationUpdateErrorMapper errorMapper = mock(PersonalInformationUpdateErrorMapper.class);
        var rawError = new UpdatePersonalInformationError("FIELD", List.of("email"), "REQUIRED");
        ApiError apiError = ApiError.field("personalInformation.email.required", "email is required", List.of("emailAddress"), "SERVER");
        when(errorMapper.toApiErrors(List.of(rawError))).thenReturn(List.of(apiError));
        PersonalInformationUpdateResponseMapper mapper = new PersonalInformationUpdateResponseMapper(errorMapper);

        MutationResponse<List<PersonalInformationUpdateAccountResponse>> response = mapper.toResponse(
                new UpdatePersonalInformationResult(false, null, null, null, List.of(rawError), List.of()));

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
        assertThat(response.result()).isNull();
        assertThat(response.messages()).isEmpty();
        assertThat(response.errors()).containsExactly(apiError);
    }

    @Test
    void mapsSelectedSuccessAndOtherAccountFailureToPartialSuccessAccountArray() {
        PersonalInformationUpdateErrorMapper errorMapper = mock(PersonalInformationUpdateErrorMapper.class);
        var failedError = new UpdatePersonalInformationError("FIELD", List.of("email"), "REQUIRED");
        ApiError apiError = ApiError.field("personalInformation.email.required", "email is required", List.of("emailAddress"), "SERVER");
        when(errorMapper.toApiErrors(List.of(failedError))).thenReturn(List.of(apiError));
        PersonalInformationUpdateResponseMapper mapper = new PersonalInformationUpdateResponseMapper(errorMapper);

        MutationResponse<List<PersonalInformationUpdateAccountResponse>> response = mapper.toResponse(
                new UpdatePersonalInformationResult(true, "260001373", "2025-12-31", "15:42:52", List.of(),
                        List.of(
                                new UpdatePersonalInformationAccountResult(
                                        true, true, "00000000118", "2", "DB", "260001373", "2025-12-31", "15:42:52", List.of()),
                                new UpdatePersonalInformationAccountResult(
                                        false, false, "00000000118", "3", "DB", "260001374", "2025-12-31", "15:42:52", List.of(failedError)))));

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo(ApiStatus.PARTIAL_SUCCESS);
        assertThat(response.result()).hasSize(2);
        assertThat(response.result().get(0).errors()).isEmpty();
        assertThat(response.result().get(1).errors()).containsExactly(apiError);
        assertThat(response.errors()).isEmpty();
        assertThat(response.messages()).hasSize(1);
    }

    @Test
    void mapsSelectedAccountFailureWithOtherSuccessToPartialSuccessResultArrayAndTopLevelSelectedErrors() {
        PersonalInformationUpdateErrorMapper errorMapper = mock(PersonalInformationUpdateErrorMapper.class);
        var selectedError = new UpdatePersonalInformationError("FIELD", List.of("email"), "REQUIRED");
        ApiError apiError = ApiError.field("personalInformation.email.required", "email is required", List.of("emailAddress"), "SERVER");
        when(errorMapper.toApiErrors(List.of(selectedError))).thenReturn(List.of(apiError));
        PersonalInformationUpdateResponseMapper mapper = new PersonalInformationUpdateResponseMapper(errorMapper);

        MutationResponse<List<PersonalInformationUpdateAccountResponse>> response = mapper.toResponse(
                new UpdatePersonalInformationResult(false, "260001374", "2025-12-31", "15:42:52", List.of(selectedError),
                        List.of(
                                new UpdatePersonalInformationAccountResult(
                                        true, false, "00000000118", "2", "DB", "260001373", "2025-12-31", "15:42:52", List.of()),
                                new UpdatePersonalInformationAccountResult(
                                        false, true, "00000000118", "3", "DB", "260001374", "2025-12-31", "15:42:52", List.of(selectedError)))));

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ApiStatus.PARTIAL_SUCCESS);
        assertThat(response.result()).hasSize(2);
        assertThat(response.result().get(0).success()).isTrue();
        assertThat(response.result().get(1).selected()).isTrue();
        assertThat(response.result().get(1).errors()).containsExactly(apiError);
        assertThat(response.messages()).isEmpty();
        assertThat(response.errors()).containsExactly(apiError);
    }
}

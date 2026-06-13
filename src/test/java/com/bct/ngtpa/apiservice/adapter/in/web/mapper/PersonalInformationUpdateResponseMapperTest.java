package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationAccountResult;
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


    @Test
    void mapsMultipleApimValidationFailuresToBlockingMutationErrors() {
        PersonalInformationUpdateErrorMapper errorMapper = mock(PersonalInformationUpdateErrorMapper.class);
        var requiredEmail = new UpdatePersonalInformationError("FIELD", List.of("email"), "REQUIRED");
        var addressRequired = new UpdatePersonalInformationError(
                "CROSS_FIELD",
                List.of("addr1", "addr2"),
                "AT_LEAST_ONE_REQUIRED");
        var apiErrors = List.of(
                ApiError.field(
                        "personalInformation.email.required",
                        "Please provide a valid email address.",
                        List.of("emailAddress"),
                        "SERVER"),
                ApiError.crossField(
                        "personalInformation.address.atLeastOneRequired",
                        "Please provide at least one address line.",
                        List.of("residentialAddressLine1", "residentialAddressLine2"),
                        "SERVER"));
        when(errorMapper.toApiErrors(List.of(requiredEmail, addressRequired))).thenReturn(apiErrors);
        PersonalInformationUpdateResponseMapper mapper = new PersonalInformationUpdateResponseMapper(errorMapper);

        MutationResponse<PersonalInformationUpdateResultResponse> response = mapper.toResponse(
                new UpdatePersonalInformationResult(
                        false,
                        null,
                        null,
                        null,
                        List.of(requiredEmail, addressRequired)));

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
        assertThat(response.result()).isNull();
        assertThat(response.messages()).isEmpty();
        assertThat(response.errors()).containsExactlyElementsOf(apiErrors);
    }

    @Test
    void mapsSelectedSuccessAndOtherAccountFailureToPartialSuccessMessageWithoutErrors() {
        PersonalInformationUpdateErrorMapper errorMapper = mock(PersonalInformationUpdateErrorMapper.class);
        var failedError = new UpdatePersonalInformationError("FIELD", List.of("email"), "REQUIRED");
        when(errorMapper.toApiErrors(List.of(failedError)))
                .thenReturn(List.of(ApiError.field(
                        "personalInformation.email.required",
                        "email is required",
                        List.of("email"),
                        "SERVER")));
        PersonalInformationUpdateResponseMapper mapper = new PersonalInformationUpdateResponseMapper(errorMapper);

        MutationResponse<PersonalInformationUpdateResultResponse> response = mapper.toResponse(
                new UpdatePersonalInformationResult(
                        true,
                        "260001373",
                        "2025-12-31",
                        "15:42:52",
                        List.of(),
                        List.of(
                                new UpdatePersonalInformationAccountResult(
                                        true, true, "00000000118", "1", "DB",
                                        "260001373", "2025-12-31", "15:42:52", List.of()),
                                new UpdatePersonalInformationAccountResult(
                                        true, false, "00000000118", "2", "DB",
                                        "260001373", "2025-12-31", "15:42:52", List.of()),
                                new UpdatePersonalInformationAccountResult(
                                        false, false, "00000000118", "3", "DB",
                                        "260001374", "2025-12-31", "15:42:52", List.of(failedError)))));

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo(ApiStatus.PARTIAL_SUCCESS);
        assertThat(response.result().refNo()).isEqualTo("260001373");
        assertThat(response.errors()).isEmpty();
        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().get(0).type()).isEqualTo("WARNING");
        assertThat(response.messages().get(0).message())
                .contains("The update was successful for the selected account and policy 00000000118 certificate 2")
                .contains("but failed for policy 00000000118 certificate 3: email is required");
    }

    @Test
    void mapsSelectedAccountFailureToValidationErrorsAndNoMessages() {
        PersonalInformationUpdateErrorMapper errorMapper = mock(PersonalInformationUpdateErrorMapper.class);
        var selectedError = new UpdatePersonalInformationError("FIELD", List.of("email"), "REQUIRED");
        ApiError apiError = ApiError.field(
                "personalInformation.email.required",
                "email is required",
                List.of("email"),
                "SERVER");
        when(errorMapper.toApiErrors(List.of(selectedError))).thenReturn(List.of(apiError));
        PersonalInformationUpdateResponseMapper mapper = new PersonalInformationUpdateResponseMapper(errorMapper);

        MutationResponse<PersonalInformationUpdateResultResponse> response = mapper.toResponse(
                new UpdatePersonalInformationResult(
                        false,
                        "260001374",
                        "2025-12-31",
                        "15:42:52",
                        List.of(selectedError),
                        List.of(
                                new UpdatePersonalInformationAccountResult(
                                        false, true, "00000000118", "3", "DB",
                                        "260001374", "2025-12-31", "15:42:52", List.of(selectedError)),
                                new UpdatePersonalInformationAccountResult(
                                        true, false, "00000000118", "2", "DB",
                                        "260001373", "2025-12-31", "15:42:52", List.of()))));

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
        assertThat(response.result()).isNull();
        assertThat(response.messages()).isEmpty();
        assertThat(response.errors()).containsExactly(apiError);
    }

}

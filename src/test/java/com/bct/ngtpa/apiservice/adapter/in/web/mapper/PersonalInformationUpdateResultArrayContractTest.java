package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalInformationUpdateResultArrayContractTest {

    @Test
    void selectedSuccessWithOtherAccountFailureReturnsAccountArrayResultAndNoTopLevelErrors() {
        PersonalInformationUpdateErrorMapper errorMapper = mock(PersonalInformationUpdateErrorMapper.class);
        var required = new UpdatePersonalInformationError("FIELD", List.of("email"), "REQUIRED");
        var invalidFormat = new UpdatePersonalInformationError("FIELD", List.of("email"), "INVALID_FORMAT");
        var addressRequired = new UpdatePersonalInformationError("CROSS_FIELD", List.of("addr1", "addr2"), "AT_LEAST_ONE_REQUIRED");
        List<UpdatePersonalInformationError> failedAccountErrors = List.of(required, invalidFormat, addressRequired);
        List<ApiError> mappedFailedAccountErrors = List.of(
                ApiError.field("personalInformation.email.required", "Email is required.", List.of("emailAddress"), "SERVER"),
                ApiError.field("personalInformation.email.invalid", "Email format is invalid.", List.of("emailAddress"), "SERVER"),
                ApiError.crossField(
                        "personalInformation.address.atLeastOneRequired",
                        "Residential address or correspondence address is required.",
                        List.of("residentialAddressLine1", "residentialAddressLine2"),
                        "SERVER"));
        when(errorMapper.toApiErrors(failedAccountErrors)).thenReturn(mappedFailedAccountErrors);

        PersonalInformationUpdateResponseMapper mapper = new PersonalInformationUpdateResponseMapper(errorMapper);

        MutationResponse<List<PersonalInformationUpdateResultResponse>> response = mapper.toResponse(
                new UpdatePersonalInformationResult(
                        true,
                        "260001373",
                        "2025-12-31",
                        "15:42:52",
                        List.of(),
                        List.of(
                                new UpdatePersonalInformationResult(
                                        true, true, "00000000118", "2", "DB",
                                        "260001373", "2025-12-31", "15:42:52", List.of()),
                                new UpdatePersonalInformationResult(
                                        false, false, "00000000118", "3", "DB",
                                        "260001374", "2025-12-31", "15:42:52", failedAccountErrors))));

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo(ApiStatus.PARTIAL_SUCCESS);
        assertThat(response.errors()).isEmpty();
        assertThat(response.messages()).hasSize(1);
        assertThat(response.result()).hasSize(2);

        PersonalInformationUpdateResultResponse selected = response.result().get(0);
        assertThat(selected.selected()).isTrue();
        assertThat(selected.success()).isTrue();
        assertThat(selected.policyNo()).isEqualTo("00000000118");
        assertThat(selected.certNo()).isEqualTo("2");
        assertThat(selected.env()).isEqualTo("DB");
        assertThat(selected.refNo()).isEqualTo("260001373");
        assertThat(selected.submitDate()).isEqualTo("2025-12-31");
        assertThat(selected.submitTime()).isEqualTo("15:42:52");
        assertThat(selected.errors()).isEmpty();

        PersonalInformationUpdateResultResponse failed = response.result().get(1);
        assertThat(failed.selected()).isFalse();
        assertThat(failed.success()).isFalse();
        assertThat(failed.policyNo()).isEqualTo("00000000118");
        assertThat(failed.certNo()).isEqualTo("3");
        assertThat(failed.env()).isEqualTo("DB");
        assertThat(failed.refNo()).isEqualTo("260001374");
        assertThat(failed.errors()).containsExactlyElementsOf(mappedFailedAccountErrors);
    }

    @Test
    void selectedFailureWithOtherAccountSuccessReturnsAccountArrayResultAndSelectedErrorsAtTopLevel() {
        PersonalInformationUpdateErrorMapper errorMapper = mock(PersonalInformationUpdateErrorMapper.class);
        var required = new UpdatePersonalInformationError("FIELD", List.of("email"), "REQUIRED");
        var invalidFormat = new UpdatePersonalInformationError("FIELD", List.of("email"), "INVALID_FORMAT");
        var addressRequired = new UpdatePersonalInformationError("CROSS_FIELD", List.of("addr1", "addr2"), "AT_LEAST_ONE_REQUIRED");
        List<UpdatePersonalInformationError> selectedErrors = List.of(required, invalidFormat, addressRequired);
        List<ApiError> mappedSelectedErrors = List.of(
                ApiError.field("personalInformation.email.required", "Email is required.", List.of("emailAddress"), "SERVER"),
                ApiError.field("personalInformation.email.invalid", "Email format is invalid.", List.of("emailAddress"), "SERVER"),
                ApiError.crossField(
                        "personalInformation.address.atLeastOneRequired",
                        "Residential address or correspondence address is required.",
                        List.of("residentialAddressLine1", "residentialAddressLine2"),
                        "SERVER"));
        when(errorMapper.toApiErrors(selectedErrors)).thenReturn(mappedSelectedErrors);

        PersonalInformationUpdateResponseMapper mapper = new PersonalInformationUpdateResponseMapper(errorMapper);

        MutationResponse<List<PersonalInformationUpdateResultResponse>> response = mapper.toResponse(
                new UpdatePersonalInformationResult(
                        false,
                        "260001374",
                        "2025-12-31",
                        "15:42:52",
                        selectedErrors,
                        List.of(
                                new UpdatePersonalInformationResult(
                                        true, false, "00000000118", "2", "DB",
                                        "260001373", "2025-12-31", "15:42:52", List.of()),
                                new UpdatePersonalInformationResult(
                                        false, true, "00000000118", "3", "DB",
                                        "260001374", "2025-12-31", "15:42:52", selectedErrors))));

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ApiStatus.PARTIAL_SUCCESS);
        assertThat(response.messages()).isEmpty();
        assertThat(response.errors()).containsExactlyElementsOf(mappedSelectedErrors);
        assertThat(response.result()).hasSize(2);

        PersonalInformationUpdateResultResponse succeeded = response.result().get(0);
        assertThat(succeeded.selected()).isFalse();
        assertThat(succeeded.success()).isTrue();
        assertThat(succeeded.certNo()).isEqualTo("2");
        assertThat(succeeded.errors()).isEmpty();

        PersonalInformationUpdateResultResponse selectedFailure = response.result().get(1);
        assertThat(selectedFailure.selected()).isTrue();
        assertThat(selectedFailure.success()).isFalse();
        assertThat(selectedFailure.certNo()).isEqualTo("3");
        assertThat(selectedFailure.errors()).containsExactlyElementsOf(mappedSelectedErrors);
    }
}

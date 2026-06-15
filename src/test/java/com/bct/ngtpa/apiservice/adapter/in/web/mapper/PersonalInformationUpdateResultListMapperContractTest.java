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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalInformationUpdateResultListMapperContractTest {

    @Test
    void mapperAcceptsResultListAndReturnsMutationResponseOfResultResponseList() throws Exception {
        Type returnType = PersonalInformationUpdateResponseMapper.class
                .getMethod("toResponse", List.class)
                .getGenericReturnType();

        assertThat(returnType).isInstanceOf(ParameterizedType.class);
        ParameterizedType mutationType = (ParameterizedType) returnType;
        assertThat(mutationType.getRawType()).isEqualTo(MutationResponse.class);

        Type resultType = mutationType.getActualTypeArguments()[0];
        assertThat(resultType).isInstanceOf(ParameterizedType.class);
        ParameterizedType resultListType = (ParameterizedType) resultType;
        assertThat(resultListType.getRawType()).isEqualTo(List.class);
        assertThat(resultListType.getActualTypeArguments()[0]).isEqualTo(PersonalInformationUpdateResultResponse.class);
    }

    @Test
    void selectedFailureWithOtherSuccessMapsToPartialSuccessResultArrayAndTopLevelErrors() {
        PersonalInformationUpdateErrorMapper errorMapper = mock(PersonalInformationUpdateErrorMapper.class);
        var required = new UpdatePersonalInformationError("FIELD", List.of("email"), "REQUIRED");
        var invalidFormat = new UpdatePersonalInformationError("FIELD", List.of("email"), "INVALID_FORMAT");
        var addressRequired = new UpdatePersonalInformationError("CROSS_FIELD", List.of("addr1", "addr2"), "AT_LEAST_ONE_REQUIRED");
        List<UpdatePersonalInformationError> selectedErrors = List.of(required, invalidFormat, addressRequired);
        List<ApiError> mappedSelectedErrors = List.of(
                ApiError.field("personalInformation.email.required", "Please provide a valid email address.", List.of("emailAddress"), "SERVER"),
                ApiError.field("personalInformation.email.invalid", "Please provide a valid email address.", List.of("emailAddress"), "SERVER"),
                ApiError.crossField("personalInformation.address.atLeastOneRequired", "Please input a residential address or correspondence address.", List.of("residentialAddressLine1", "residentialAddressLine2"), "SERVER"));
        when(errorMapper.toApiErrors(selectedErrors)).thenReturn(mappedSelectedErrors);

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
                        selectedErrors)));

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo(ApiStatus.PARTIAL_SUCCESS);
        assertThat(response.messages()).isEmpty();
        assertThat(response.errors()).containsExactlyElementsOf(mappedSelectedErrors);
        assertThat(response.result()).hasSize(2);
        assertThat(response.result().get(0).selected()).isFalse();
        assertThat(response.result().get(0).success()).isTrue();
        assertThat(response.result().get(1).selected()).isTrue();
        assertThat(response.result().get(1).success()).isFalse();
        assertThat(response.result().get(1).policyNo()).isEqualTo("00000000118");
        assertThat(response.result().get(1).certNo()).isEqualTo("3");
        assertThat(response.result().get(1).env()).isEqualTo("DB");
        assertThat(response.result().get(1).errors()).containsExactlyElementsOf(mappedSelectedErrors);
    }

    @Test
    void selectedSuccessWithOtherFailureMapsToPartialSuccessWarningAndNoTopLevelErrors() {
        PersonalInformationUpdateErrorMapper errorMapper = mock(PersonalInformationUpdateErrorMapper.class);
        var required = new UpdatePersonalInformationError("FIELD", List.of("email"), "REQUIRED");
        var invalidFormat = new UpdatePersonalInformationError("FIELD", List.of("email"), "INVALID_FORMAT");
        var addressRequired = new UpdatePersonalInformationError("CROSS_FIELD", List.of("addr1", "addr2"), "AT_LEAST_ONE_REQUIRED");
        List<UpdatePersonalInformationError> failedErrors = List.of(required, invalidFormat, addressRequired);
        List<ApiError> mappedFailedErrors = List.of(
                ApiError.field("personalInformation.email.required", "Please provide a valid email address.", List.of("emailAddress"), "SERVER"),
                ApiError.field("personalInformation.email.invalid", "Please provide a valid email address.", List.of("emailAddress"), "SERVER"),
                ApiError.crossField("personalInformation.address.atLeastOneRequired", "Please input a residential address or correspondence address.", List.of("residentialAddressLine1", "residentialAddressLine2"), "SERVER"));
        when(errorMapper.toApiErrors(failedErrors)).thenReturn(mappedFailedErrors);

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
                        failedErrors)));

        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo(ApiStatus.PARTIAL_SUCCESS);
        assertThat(response.messages()).hasSize(1);
        assertThat(response.messages().getFirst().code()).isEqualTo("personalInformation.update.partialSuccess");
        assertThat(response.errors()).isEmpty();
        assertThat(response.result()).hasSize(2);
        assertThat(response.result().get(0).selected()).isTrue();
        assertThat(response.result().get(0).success()).isTrue();
        assertThat(response.result().get(1).selected()).isFalse();
        assertThat(response.result().get(1).success()).isFalse();
        assertThat(response.result().get(1).errors()).containsExactlyElementsOf(mappedFailedErrors);
    }
}

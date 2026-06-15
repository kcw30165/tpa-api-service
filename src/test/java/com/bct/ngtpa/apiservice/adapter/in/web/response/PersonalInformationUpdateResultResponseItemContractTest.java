package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalInformationUpdateResultResponseItemContractTest {

    @Test
    void responseItemRecordShapeMatchesUpdateResultArrayContract() {
        assertThat(PersonalInformationUpdateResultResponse.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly(
                        "selected",
                        "success",
                        "policyNo",
                        "certNo",
                        "env",
                        "refNo",
                        "submitDate",
                        "submitTime",
                        "errors");
    }

    @Test
    void responseItemErrorsDefaultToEmptyAndAreDefensivelyCopied() {
        ApiError error = ApiError.field(
                "personalInformation.email.required",
                "Please provide a valid email address.",
                List.of("emailAddress"),
                "SERVER");
        List<ApiError> mutableErrors = new ArrayList<>();
        mutableErrors.add(error);

        PersonalInformationUpdateResultResponse result = new PersonalInformationUpdateResultResponse(
                true,
                false,
                "00000000118",
                "3",
                "DB",
                "260001374",
                "2025-12-31",
                "15:42:52",
                mutableErrors);
        mutableErrors.clear();

        assertThat(result.errors()).containsExactly(error);
        assertThat(new PersonalInformationUpdateResultResponse(
                false,
                true,
                "00000000118",
                "2",
                "DB",
                "260001373",
                "2025-12-31",
                "15:42:52",
                null).errors()).isEmpty();
        assertThatThrownBy(() -> result.errors().add(error))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void redundantAccountResponseDtoIsRemoved() {
        assertThatThrownBy(() -> Class.forName(
                "com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateAccountResponse"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}

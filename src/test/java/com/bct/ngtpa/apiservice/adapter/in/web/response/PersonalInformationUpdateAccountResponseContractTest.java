package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalInformationUpdateAccountResponseContractTest {

    @Test
    void accountResponseRecordShapeMatchesFrontendAccountArrayContract() {
        assertThat(PersonalInformationUpdateAccountResponse.class.getRecordComponents())
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
    void errorsDefaultToEmptyAndAreDefensivelyCopied() {
        ApiError error = ApiError.field(
                "personalInformation.email.required",
                "Email is required.",
                List.of("emailAddress"),
                "SERVER");
        List<ApiError> mutableErrors = new ArrayList<>();
        mutableErrors.add(error);

        PersonalInformationUpdateAccountResponse account = new PersonalInformationUpdateAccountResponse(
                false,
                false,
                "00000000118",
                "3",
                "DB",
                "260001374",
                "2025-12-31",
                "15:42:52",
                mutableErrors);
        mutableErrors.clear();

        assertThat(account.errors()).containsExactly(error);
        assertThat(new PersonalInformationUpdateAccountResponse(
                true,
                true,
                "00000000118",
                "2",
                "DB",
                "260001373",
                "2025-12-31",
                "15:42:52",
                null).errors()).isEmpty();
        assertThatThrownBy(() -> account.errors().add(error))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

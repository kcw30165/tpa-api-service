package com.bct.ngtpa.apiservice.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UpdatePersonalInformationResultItemContractTest {

    @Test
    void updatePersonalInformationResultRepresentsOneApimDataItem() {
        assertThat(UpdatePersonalInformationResult.class.getRecordComponents())
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
    void updatePersonalInformationResultHasFlatAccountAndSubmissionFields() {
        UpdatePersonalInformationError error = new UpdatePersonalInformationError(
                "FIELD",
                List.of("email"),
                "REQUIRED");

        UpdatePersonalInformationResult result = new UpdatePersonalInformationResult(
                true,
                false,
                "00000000118",
                "3",
                "DB",
                "260001374",
                "2025-12-31",
                "15:42:52",
                List.of(error));

        assertThat(result.selected()).isTrue();
        assertThat(result.success()).isFalse();
        assertThat(result.policyNo()).isEqualTo("00000000118");
        assertThat(result.certNo()).isEqualTo("3");
        assertThat(result.env()).isEqualTo("DB");
        assertThat(result.refNo()).isEqualTo("260001374");
        assertThat(result.submitDate()).isEqualTo("2025-12-31");
        assertThat(result.submitTime()).isEqualTo("15:42:52");
        assertThat(result.errors()).containsExactly(error);
    }

    @Test
    void updatePersonalInformationResultErrorsDefaultToEmptyAndAreDefensivelyCopied() {
        UpdatePersonalInformationError error = new UpdatePersonalInformationError(
                "FIELD",
                List.of("email"),
                "REQUIRED");
        List<UpdatePersonalInformationError> mutableErrors = new ArrayList<>();
        mutableErrors.add(error);

        UpdatePersonalInformationResult result = new UpdatePersonalInformationResult(
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

        assertThat(result.errors()).containsExactly(error);
        assertThat(new UpdatePersonalInformationResult(
                true,
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
    void oldNestedAccountResultDtoSourceIsRemoved() {
        assertThat(Path.of("src/main/java/com/bct/ngtpa/apiservice/application/dto/UpdatePersonalInformationAccountResult.java"))
                .doesNotExist();
        assertThat(Files.exists(Path.of("src/main/java/com/bct/ngtpa/apiservice/application/dto/UpdatePersonalInformationResult.java")))
                .isTrue();
    }
}

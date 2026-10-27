package com.bct.ngtpa.apiservice.adapter.in.web.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalInformationUpdateResultResponseItemContractTest {
  @Test
  void redundantAccountResponseDtoIsRemoved() {
    assertThatThrownBy(
            () ->
                Class.forName(
                    "com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateAccountResponse"))
        .isInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void accountShapeContainsOnlyApprovedFieldsAndOtherErrorsContainOnlyIdentifierAndErrors() {
    assertThat(PersonalInformationUpdateResultResponse.Account.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .containsExactly("accountIdentifier", "refNo", "submitDate", "submitTime");
    assertThat(PersonalInformationUpdateResultResponse.AccountErrors.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .containsExactly("accountIdentifier", "errors");
    assertThat(MappedError.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getName)
        .containsExactly("type", "code", "targets");
  }

  @Test
  void allResultListsAreDefensivelyCopiedAndImmutable() {
    var error = new MappedError(null, "personalInformation.update.rejected", List.of());
    var errors = new ArrayList<>(List.of(error));
    var failed =
        new PersonalInformationUpdateResultResponse.AccountErrors("00000000118-0003", errors);
    errors.clear();
    assertThat(failed.errors()).containsExactly(error);
    assertThatThrownBy(() -> failed.errors().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    var row =
        new PersonalInformationUpdateResultResponse.Account("00000000118-0004", null, "", " ");
    var rows = new ArrayList<>(List.of(row));
    var failedRows = new ArrayList<>(List.of(failed));
    var data = new PersonalInformationUpdateResultResponse.Data(null, rows, failedRows);
    rows.clear();
    failedRows.clear();
    assertThat(data.rows()).containsExactly(row);
    assertThat(data.errors()).containsExactly(failed);
    assertThatThrownBy(() -> data.rows().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> data.errors().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }
}

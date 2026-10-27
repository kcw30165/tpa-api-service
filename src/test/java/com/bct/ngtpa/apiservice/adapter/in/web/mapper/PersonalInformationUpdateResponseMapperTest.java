package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MappedError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse.Account;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse.AccountErrors;
import com.bct.ngtpa.apiservice.adapter.in.web.response.UpdatePersonalInformationResponse.Message;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationError;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.exception.PersonalInformationUpdateException;
import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalInformationUpdateResponseMapperTest {
  @Test
  void singleSelectedSuccessIsUpdatedWithFormattedIdentifierAndVerbatimMetadata() {
    var response = mapper().toResponse(row(true, true, "2"));
    assertThat(response.success()).isTrue();
    assertThat(response.status()).isEqualTo(ApiStatus.UPDATED);
    assertThat(response.result().data().selectedAccount().accountIdentifier())
        .isEqualTo("00000000118-0002");
    assertThat(response.result().data().selectedAccount().refNo()).isNull();
    assertThat(response.result().data().selectedAccount().submitDate()).isEmpty();
    assertThat(response.result().data().selectedAccount().submitTime()).isEqualTo(" ");
    assertThat(response.result().data().rows()).isEmpty();
    assertThat(response.result().data().errors()).isEmpty();
    assertThat(response.messages())
        .extracting(Message::code)
        .containsExactly("personalInformation.update.success");
    assertThat(response.errors()).isEmpty();
  }

  @Test
  void selectedFailureIsValidationFailedAndNeverDuplicatedInData() {
    var response = mapper().toResponse(row(true, false, "2"));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(response.result().data().selectedAccount()).isNull();
    assertThat(response.result().data().rows()).isEmpty();
    assertThat(response.result().data().errors()).isEmpty();
    assertThat(response.errors())
        .extracting(MappedError::code)
        .containsExactly("personalInformation.field.required");
    assertThat(response.messages()).isEmpty();
  }

  @Test
  void mixedOtherAccountsArePartitionedWithoutDuplicatingSelectedAccount() {
    var response =
        mapper()
            .toResponse(
                List.of(
                    row(false, true, "3"),
                    row(true, true, "2"),
                    row(false, false, "4"),
                    row(false, true, "5")));
    assertThat(response.status()).isEqualTo(ApiStatus.PARTIAL_SUCCESS);
    assertThat(response.result().data().rows())
        .extracting(Account::accountIdentifier)
        .containsExactly("00000000118-0003", "00000000118-0005");
    assertThat(response.result().data().errors())
        .extracting(AccountErrors::accountIdentifier)
        .containsExactly("00000000118-0004");
    assertThat(response.result().data().errors().getFirst().errors())
        .extracting(MappedError::code)
        .containsExactly("personalInformation.field.required");
    assertThat(response.errors()).isEmpty();
    assertThat(response.messages())
        .extracting(Message::code)
        .containsExactly("personalInformation.update.partialSuccess");
  }

  @Test
  void selectedFailureWithSuccessfulOtherAccountIsNotPartialSuccess() {
    var response = mapper().toResponse(List.of(row(false, true, "3"), row(true, false, "2")));
    assertThat(response.success()).isFalse();
    assertThat(response.status()).isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(response.result().data().rows()).hasSize(1);
    assertThat(response.result().data().selectedAccount()).isNull();
    assertThat(response.messages()).isEmpty();
  }

  @Test
  void emptyOtherErrorsUseTheSt3NullTypeFallback() {
    var other =
        new UpdatePersonalInformationResult(
            false, false, "118", "3", "DB", null, null, null, List.of());
    var response = mapper().toResponse(List.of(row(true, true, "2"), other));
    var error = response.result().data().errors().getFirst().errors().getFirst();
    assertThat(error.type()).isNull();
    assertThat(error.code()).isEqualTo("personalInformation.update.rejected");
    assertThat(error.targets()).isEmpty();
  }

  @Test
  void formattingUsesInjectedWidthsAndPaddingRatherThanHardcodedValues() {
    var mapper =
        new PersonalInformationUpdateResponseMapper(
            new PersonalInformationUpdateErrorMapper(null),
            new AccountIdentifierFormatter(6, 3, '9'));
    var response = mapper.toResponse(List.of(row(true, true, "2"), row(false, false, "3")));
    assertThat(response.result().data().selectedAccount().accountIdentifier())
        .isEqualTo("999118-992");
    assertThat(response.result().data().errors().getFirst().accountIdentifier())
        .isEqualTo("999118-993");
  }

  @Test
  void absentResultsOrSelectedAccountFailThroughTheExistingPutUnavailableException() {
    var responseMapper = mapper();
    List<UpdatePersonalInformationResult> emptyResults = List.of();
    var otherResults = List.of(row(false, true, "3"));
    assertThatThrownBy(
            () -> responseMapper.toResponse((List<UpdatePersonalInformationResult>) null))
        .isInstanceOf(PersonalInformationUpdateException.class);
    assertThatThrownBy(() -> responseMapper.toResponse(emptyResults))
        .isInstanceOf(PersonalInformationUpdateException.class);
    assertThatThrownBy(() -> responseMapper.toResponse((UpdatePersonalInformationResult) null))
        .isInstanceOf(PersonalInformationUpdateException.class);
    assertThatThrownBy(() -> responseMapper.toResponse(otherResults))
        .isInstanceOf(PersonalInformationUpdateException.class);
  }

  static PersonalInformationUpdateResponseMapper mapper() {
    return new PersonalInformationUpdateResponseMapper(
        new PersonalInformationUpdateErrorMapper(null), new AccountIdentifierFormatter(11, 4, '0'));
  }

  static UpdatePersonalInformationResult row(boolean selected, boolean success, String cert) {
    return new UpdatePersonalInformationResult(
        selected,
        success,
        "118",
        cert,
        "DB",
        null,
        "",
        " ",
        success
            ? List.of()
            : List.of(new UpdatePersonalInformationError("FIELD", List.of(), "REQUIRED")));
  }
}

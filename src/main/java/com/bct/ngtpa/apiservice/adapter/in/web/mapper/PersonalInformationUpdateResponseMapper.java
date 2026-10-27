package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse.Account;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse.AccountErrors;
import com.bct.ngtpa.apiservice.adapter.in.web.response.PersonalInformationUpdateResultResponse.Data;
import com.bct.ngtpa.apiservice.adapter.in.web.response.UpdatePersonalInformationResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.response.UpdatePersonalInformationResponse.Message;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.exception.PersonalInformationUpdateException;
import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PersonalInformationUpdateResponseMapper {
  private final PersonalInformationUpdateErrorMapper errorMapper;
  private final AccountIdentifierFormatter formatter;

  public PersonalInformationUpdateResponseMapper(
      PersonalInformationUpdateErrorMapper errorMapper, AccountIdentifierFormatter formatter) {
    this.errorMapper = errorMapper;
    this.formatter = formatter;
  }

  public UpdatePersonalInformationResponse toResponse(
      List<UpdatePersonalInformationResult> results) {
    if (results == null || results.isEmpty()) {
      throw PersonalInformationUpdateException.systemFailure();
    }
    UpdatePersonalInformationResult selected =
        results.stream()
            .filter(UpdatePersonalInformationResult::selected)
            .findFirst()
            .orElseThrow(PersonalInformationUpdateException::systemFailure);
    List<Account> rows =
        results.stream()
            .filter(row -> !row.selected() && row.success())
            .map(this::account)
            .toList();
    List<AccountErrors> otherErrors =
        results.stream()
            .filter(row -> !row.selected() && !row.success())
            .map(row -> new AccountErrors(identifier(row), errorMapper.toMappedErrors(row)))
            .toList();
    var result =
        new PersonalInformationUpdateResultResponse(
            new Data(selected.success() ? account(selected) : null, rows, otherErrors));
    if (!selected.success()) {
      return new UpdatePersonalInformationResponse(
          false,
          ApiStatus.VALIDATION_FAILED,
          result,
          List.of(),
          errorMapper.toMappedErrors(selected));
    }
    boolean partial = !otherErrors.isEmpty();
    return new UpdatePersonalInformationResponse(
        true,
        partial ? ApiStatus.PARTIAL_SUCCESS : ApiStatus.UPDATED,
        result,
        List.of(
            new Message(
                partial
                    ? "personalInformation.update.partialSuccess"
                    : "personalInformation.update.success")),
        List.of());
  }

  public UpdatePersonalInformationResponse toResponse(UpdatePersonalInformationResult result) {
    return toResponse(result == null ? List.of() : List.of(result));
  }

  private Account account(UpdatePersonalInformationResult row) {
    return new Account(identifier(row), row.refNo(), row.submitDate(), row.submitTime());
  }

  private String identifier(UpdatePersonalInformationResult row) {
    return formatter.format(row.policyNo(), row.certNo());
  }
}

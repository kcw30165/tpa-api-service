package com.bct.ngtpa.apiservice.adapter.in.web.account;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps resolved {@link AccountContext} value(s) into the account endpoints' HTTP response shapes.
 */
public final class AccountWebMapper {

  public MutationResponse<AccountResultResponse> toAccountResponse(AccountContext account) {
    return MutationResponse.success(
        ApiStatus.SUCCESS, new AccountResultResponse(toDetails(account)), List.of());
  }

  public MutationResponse<AccountsResultResponse> toAccountsResponse(
      List<AccountContext> accounts) {
    List<AccountDetailsResponse> details =
        accounts.stream().map(this::toDetails).collect(Collectors.toList());
    return MutationResponse.success(
        ApiStatus.SUCCESS,
        new AccountsResultResponse(new AccountsDataResponse(details)),
        List.of());
  }

  private AccountDetailsResponse toDetails(AccountContext account) {
    return new AccountDetailsResponse(
        account.accountRef(),
        account.policyNo(),
        account.certNo(),
        account.accountEnv(),
        account.trustCode(),
        account.schemeType());
  }
}

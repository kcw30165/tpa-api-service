package com.bct.ngtpa.apiservice.adapter.in.web.accountswitch;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import java.util.List;

/** Maps the resolved {@link AccountContext} into the switch endpoint's HTTP response shape. */
public final class AccountSwitchWebMapper {

  public MutationResponse<AccountSwitchResponse> toResponse(AccountContext account) {
    AccountSwitchResponse data =
        new AccountSwitchResponse(
            account.accountRef(),
            account.policyNo(),
            account.certNo(),
            account.accountEnv(),
            account.trustCode(),
            account.schemeType());
    return MutationResponse.success(ApiStatus.SUCCESS, data, List.of());
  }
}

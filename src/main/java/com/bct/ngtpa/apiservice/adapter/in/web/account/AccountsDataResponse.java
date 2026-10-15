package com.bct.ngtpa.apiservice.adapter.in.web.account;

import java.util.List;

/** Data payload for the list-all-accounts response: {@code result.data.accounts}. */
public record AccountsDataResponse(List<AccountDetailsResponse> accounts) {}

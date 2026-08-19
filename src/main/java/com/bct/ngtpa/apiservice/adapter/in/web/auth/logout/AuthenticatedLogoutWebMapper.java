package com.bct.ngtpa.apiservice.adapter.in.web.auth.logout;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiError;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.response.MutationResponse;
import com.bct.ngtpa.apiservice.application.usecase.auth.logout.AuthenticatedLogoutResult;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;

public final class AuthenticatedLogoutWebMapper {
  public MutationResponse<Void> toResponse(AuthenticatedLogoutResult result) {
    if (result.successful()) {
      return MutationResponse.success(ApiStatus.SUCCESS, null, List.of());
    }
    return MutationResponse.failure(
        ApiStatus.SYSTEM_ERROR,
        List.of(ApiError.system(ErrorCodes.SYSTEM_UNEXPECTED, null, "SERVER")));
  }

  public MutationResponse<Void> idempotentSuccess() {
    return MutationResponse.success(ApiStatus.SUCCESS, null, List.of());
  }
}

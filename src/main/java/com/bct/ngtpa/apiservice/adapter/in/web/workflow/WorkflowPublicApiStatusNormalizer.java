package com.bct.ngtpa.apiservice.adapter.in.web.workflow;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import java.util.Objects;

public final class WorkflowPublicApiStatusNormalizer {
  private WorkflowPublicApiStatusNormalizer() {}

  public static ApiStatus normalize(ApiStatus status) {
    Objects.requireNonNull(status, "status must not be null");
    return switch (status) {
      case DOWNSTREAM_REJECTED -> ApiStatus.BUSINESS_REJECTED;
      case DOWNSTREAM_ERROR -> ApiStatus.SYSTEM_ERROR;
      default -> status;
    };
  }
}

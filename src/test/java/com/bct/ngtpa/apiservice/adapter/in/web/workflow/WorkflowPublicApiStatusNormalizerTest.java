package com.bct.ngtpa.apiservice.adapter.in.web.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import org.junit.jupiter.api.Test;

class WorkflowPublicApiStatusNormalizerTest {

  @Test
  void normalizesDownstreamStatusesForForgotPasswordPublicBoundary() {
    assertThat(WorkflowPublicApiStatusNormalizer.normalize(ApiStatus.DOWNSTREAM_REJECTED))
        .isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(WorkflowPublicApiStatusNormalizer.normalize(ApiStatus.DOWNSTREAM_ERROR))
        .isEqualTo(ApiStatus.SYSTEM_ERROR);
  }

  @Test
  void keepsApprovedPublicStatusesUnchanged() {
    assertThat(WorkflowPublicApiStatusNormalizer.normalize(ApiStatus.SUCCESS))
        .isEqualTo(ApiStatus.SUCCESS);
    assertThat(WorkflowPublicApiStatusNormalizer.normalize(ApiStatus.VALIDATION_FAILED))
        .isEqualTo(ApiStatus.VALIDATION_FAILED);
    assertThat(WorkflowPublicApiStatusNormalizer.normalize(ApiStatus.BUSINESS_REJECTED))
        .isEqualTo(ApiStatus.BUSINESS_REJECTED);
    assertThat(WorkflowPublicApiStatusNormalizer.normalize(ApiStatus.SYSTEM_ERROR))
        .isEqualTo(ApiStatus.SYSTEM_ERROR);
  }
}

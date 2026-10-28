package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

/** Recovery resubmission is independent of transient-business retry eligibility. */
public enum ApimResubmissionPolicy {
  ALLOW_RECOVERY,
  NEVER
}

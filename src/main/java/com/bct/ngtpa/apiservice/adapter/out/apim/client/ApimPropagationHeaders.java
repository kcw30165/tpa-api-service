package com.bct.ngtpa.apiservice.adapter.out.apim.client;

/** Outbound APIM propagation header names for authenticated portal-access identity. */
public final class ApimPropagationHeaders {

  public static final String TRUST_HEADER = "X-Trust";
  public static final String USER_ROLE_HEADER = "X-User-Role";

  private ApimPropagationHeaders() {}
}

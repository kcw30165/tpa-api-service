package com.bct.ngtpa.apiservice.adapter.out.apim;

/** Shared technical APIM failure categories. Business rejection remains feature-owned. */
public enum ApimFailureCategory {
  DOWNSTREAM_CONTRACT,
  DOWNSTREAM_TRANSPORT,
  LOCAL_SYSTEM
}

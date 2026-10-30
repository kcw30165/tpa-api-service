package com.bct.ngtpa.apiservice.application.port.out;

import com.bct.ngtpa.apiservice.domain.passwordvalidation.PayloadDigest;

public interface OperationPayloadBindingPort {
  /** Takes raw JSON, before duplicate keys or numeric precision can be lost by DTO mapping. */
  PayloadDigest digest(String operationPayloadJson);
}

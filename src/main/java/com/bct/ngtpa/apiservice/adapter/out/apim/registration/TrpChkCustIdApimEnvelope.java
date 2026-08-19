package com.bct.ngtpa.apiservice.adapter.out.apim.registration;

import com.bct.ngtpa.apiservice.adapter.out.apim.registration.dto.TrpChkCustIdApimDataItem;
import java.util.List;

public record TrpChkCustIdApimEnvelope(String errMessage, List<TrpChkCustIdApimDataItem> data) {
  public TrpChkCustIdApimEnvelope {
    errMessage = errMessage == null ? "" : errMessage.trim();
    data = data == null ? List.of() : List.copyOf(data);
  }

  public static TrpChkCustIdApimEnvelope success() {
    return available("<redacted>");
  }

  public static TrpChkCustIdApimEnvelope available(String customerId) {
    return new TrpChkCustIdApimEnvelope(
        "", List.of(new TrpChkCustIdApimDataItem(customerId, Boolean.FALSE)));
  }

  public static TrpChkCustIdApimEnvelope exists(String customerId) {
    return new TrpChkCustIdApimEnvelope(
        "", List.of(new TrpChkCustIdApimDataItem(customerId, Boolean.TRUE)));
  }

  public static TrpChkCustIdApimEnvelope businessRejected(String errMessage) {
    return new TrpChkCustIdApimEnvelope(errMessage, List.of());
  }

  @Override
  public String toString() {
    return "TrpChkCustIdApimEnvelope[errMessage=" + errMessage + ", dataCount=" + data.size() + "]";
  }
}

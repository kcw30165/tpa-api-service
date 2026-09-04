package com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss;

import com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss.dto.TrpGetInvestGlApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.accountgainloss.dto.TrpGetInvestGlFund;
import java.util.HashSet;
import java.util.List;

public final class AccountGainLossApimContentValidator {
  private AccountGainLossApimContentValidator() {}

  public static TrpGetInvestGlApimDataItem requireSingleData(
      List<TrpGetInvestGlApimDataItem> data) {
    if (data == null || data.size() != 1 || data.getFirst() == null) {
      throw new IllegalArgumentException(
          "TRPGetInvestGL response.data must contain exactly one non-null item");
    }
    var item = data.getFirst();
    if (item.priceDate() == null || item.funds() == null) {
      throw new IllegalArgumentException("TRPGetInvestGL data item is incomplete");
    }
    var codes = new HashSet<String>();
    for (TrpGetInvestGlFund fund : item.funds()) {
      if (fund == null
          || isBlank(fund.fundCode())
          || fund.fundSequence() == null
          || fund.gainLoss() == null
          || (isBlank(fund.fundName()) && isBlank(fund.chineseFundName()))
          || !codes.add(fund.fundCode().trim())) {
        throw new IllegalArgumentException("TRPGetInvestGL fund content is invalid");
      }
    }
    return item;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}

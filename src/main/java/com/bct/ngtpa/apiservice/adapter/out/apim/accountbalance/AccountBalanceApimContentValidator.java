package com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance;

import com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto.AccountBalanceCurrency;
import com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto.AccountBalanceDisplayOptions;
import com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto.TrpGetMemberAccBalApimDataItem;
import java.util.List;

public final class AccountBalanceApimContentValidator {
  private AccountBalanceApimContentValidator() {}

  public static TrpGetMemberAccBalApimDataItem requireSingleData(
      List<TrpGetMemberAccBalApimDataItem> data) {
    if (data == null || data.size() != 1 || data.getFirst() == null) {
      throw new IllegalArgumentException(
          "TRPGetMemberAccBal response.data must contain exactly one non-null item");
    }
    var item = data.getFirst();
    if (item.asOfDate() == null) {
      throw new IllegalArgumentException(
          "TRPGetMemberAccBal response.data[0].as-of-date must not be null");
    }
    requireSingleNonNull(item.displayOptions(), "displayOptions");
    requireSingleNonNull(item.currencies(), "curr");
    if (item.displaySources() == null
        || item.fundDetails() == null
        || item.accountBalances() == null) {
      throw new IllegalArgumentException("TRPGetMemberAccBal data item arrays must not be null");
    }
    return item;
  }

  public static AccountBalanceDisplayOptions requireDisplayOptions(
      TrpGetMemberAccBalApimDataItem item) {
    return requireSingleNonNull(item.displayOptions(), "displayOptions");
  }

  public static AccountBalanceCurrency requireCurrency(TrpGetMemberAccBalApimDataItem item) {
    return requireSingleNonNull(item.currencies(), "curr");
  }

  private static <T> T requireSingleNonNull(List<T> values, String fieldName) {
    if (values == null || values.size() != 1 || values.getFirst() == null) {
      throw new IllegalArgumentException(
          "TRPGetMemberAccBal " + fieldName + " must contain exactly one non-null item");
    }
    return values.getFirst();
  }
}

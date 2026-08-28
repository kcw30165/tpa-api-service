package com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance;
import com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance.dto.TrpGetMemberAccBalApimDataItem;
import java.util.List;
public final class AccountBalanceApimContentValidator {
 private AccountBalanceApimContentValidator() {}
 public static TrpGetMemberAccBalApimDataItem requireSingleData(List<TrpGetMemberAccBalApimDataItem> data) {
  if (data == null || data.size() != 1 || data.getFirst() == null) throw new IllegalArgumentException("TRPGetMemberAccBal response.data must contain exactly one non-null item");
  var item=data.getFirst();
  if (item.displayOptions()==null || item.currency()==null || item.displaySources()==null || item.fundDetails()==null || item.accountBalances()==null) throw new IllegalArgumentException("TRPGetMemberAccBal data item is incomplete");
  return item;
 }
}

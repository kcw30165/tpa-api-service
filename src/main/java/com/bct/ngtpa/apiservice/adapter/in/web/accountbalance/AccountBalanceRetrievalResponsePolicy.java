package com.bct.ngtpa.apiservice.adapter.in.web.accountbalance;
import com.bct.ngtpa.apiservice.adapter.in.web.response.*;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import java.util.List;
import org.springframework.http.*;
public final class AccountBalanceRetrievalResponsePolicy {
 private static final String SOURCE_SERVER="SERVER";
 private AccountBalanceRetrievalResponsePolicy() {}
 public static ResponseEntity<MutationResponse<Void>> map(AccountBalanceRetrievalException ex) {
  return switch (ex.reason()) {
   case ACCOUNT_NOT_FOUND -> business("err.ab.account.not.exist");
   case ACCOUNT_MISMATCH -> business("err.ab.account.mismatch");
   case SYSTEM_FAILURE -> new ResponseEntity<>(MutationResponse.failure(ApiStatus.SYSTEM_ERROR,List.of(ApiError.system("err.system.unavailable",null,SOURCE_SERVER))),HttpStatus.SERVICE_UNAVAILABLE);
  };
 }
 private static ResponseEntity<MutationResponse<Void>> business(String code) {
  return new ResponseEntity<>(MutationResponse.failure(ApiStatus.BUSINESS_REJECTED,List.of(ApiError.business(code,null,List.of(),SOURCE_SERVER))),HttpStatus.UNPROCESSABLE_ENTITY);
 }
}

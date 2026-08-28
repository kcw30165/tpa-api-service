package com.bct.ngtpa.apiservice.adapter.in.web.accountbalance;
import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.adapter.in.web.response.*;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import org.junit.jupiter.api.Test;
class AccountBalanceRetrievalResponsePolicyTest {
 @Test void mapsLockedBusinessAndSystemContracts() {
  var notFound=AccountBalanceRetrievalResponsePolicy.map(AccountBalanceRetrievalException.accountNotFound());
  assertThat(notFound.getStatusCode().value()).isEqualTo(422);
  assertThat(notFound.getBody().status()).isEqualTo(ApiStatus.BUSINESS_REJECTED);
  assertThat(notFound.getBody().errors().getFirst().type()).isEqualTo("BUSINESS");
  assertThat(notFound.getBody().errors().getFirst().code()).isEqualTo("err.ab.account.not.exist");
  var mismatch=AccountBalanceRetrievalResponsePolicy.map(AccountBalanceRetrievalException.accountMismatch());
  assertThat(mismatch.getBody().errors().getFirst().code()).isEqualTo("err.ab.account.mismatch");
  var system=AccountBalanceRetrievalResponsePolicy.map(AccountBalanceRetrievalException.systemFailure());
  assertThat(system.getStatusCode().value()).isEqualTo(503);
  assertThat(system.getBody().status()).isEqualTo(ApiStatus.SYSTEM_ERROR);
  assertThat(system.getBody().errors().getFirst().type()).isEqualTo("SYSTEM");
  assertThat(system.getBody().errors().getFirst().code()).isEqualTo("err.system.unavailable");
 }
}

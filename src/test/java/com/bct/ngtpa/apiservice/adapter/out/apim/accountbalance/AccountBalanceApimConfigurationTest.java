package com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class AccountBalanceApimConfigurationTest {
 @Test void validatesAndTrimsPath(){var p=new AccountBalanceApimProperties();p.setPath(" /x ");p.validate();assertThat(p.getPath()).isEqualTo("/x");}
 @Test void rejectsBlankPath(){var p=new AccountBalanceApimProperties();assertThatThrownBy(p::validate).isInstanceOf(IllegalStateException.class).hasMessageContaining("apim.account-balance.path");}
}

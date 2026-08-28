package com.bct.ngtpa.apiservice.application.service.accountbalance;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.*; import org.junit.jupiter.api.Test;
class AccountBalancePortfolioViewConsistencyTest {
 @Test void bothViewsProjectTheSameNormalizedHoldingLeaves(){ var data=DefaultSubDisplayLevelPortfolioBuilderTest.TestData.data(); var fundView=new DefaultFundLevelPortfolioBuilder().build(data); var subView=new DefaultSubDisplayLevelPortfolioBuilder().build(data); Set<String> a=new TreeSet<>(),b=new TreeSet<>(); fundView.displayAccounts().forEach(d->d.funds().forEach(f->f.subDisplayAccounts().forEach(s->a.add(d.displayAccountId()+"|"+s.subDisplayAccountId()+"|"+f.fundCode()+"|"+s.unitBalance()+"|"+s.accountBalance())))); subView.displayAccounts().forEach(d->d.subDisplayAccounts().forEach(s->s.funds().forEach(f->b.add(d.displayAccountId()+"|"+s.subDisplayAccountId()+"|"+f.fundCode()+"|"+f.unitBalance()+"|"+f.accountBalance())))); assertThat(b).isEqualTo(a); }
}

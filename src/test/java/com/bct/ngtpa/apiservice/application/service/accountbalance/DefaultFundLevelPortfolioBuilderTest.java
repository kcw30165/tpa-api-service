package com.bct.ngtpa.apiservice.application.service.accountbalance;
import static org.assertj.core.api.Assertions.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.normalized.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.portfolio.*;
import com.bct.ngtpa.apiservice.application.dto.accountbalance.raw.*;
import com.bct.ngtpa.apiservice.application.exception.AccountBalanceRetrievalException;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
class DefaultFundLevelPortfolioBuilderTest {
 private final DefaultFundLevelPortfolioBuilder builder=new DefaultFundLevelPortfolioBuilder();
 @Test void buildsHeldHierarchyAndAggregatesInDeterministicOrder(){
  var data=normalized(List.of(source("ZZ","B",20),source("ZZ","A",10),source("AA","C",10)),List.of(fund("F2",20),fund("F1",10)),List.of(holding("ZZ","B","F1","2.00","20.00"),holding("ZZ","A","F1","1.0","10.0"),holding("AA","C","F2","3","30")));
  var result=builder.build(data);
  assertThat(result.displayAccounts()).extracting(RawFundLevelDisplayAccount::displayAccountId).containsExactly("AA","ZZ");
  var zz=result.displayAccounts().get(1); assertThat(zz.displaySequence()).isEqualTo(10);
  assertThat(zz.funds()).extracting(RawFundLevelFund::fundCode).containsExactly("F1");
  var f=zz.funds().getFirst(); assertThat(f.unitBalance()).isEqualByComparingTo("3.00"); assertThat(f.accountBalance()).isEqualByComparingTo("30.00");
  assertThat(f.subDisplayAccounts()).extracting(RawFundLevelSubDisplayAccount::subDisplayAccountId).containsExactly("A","B");
 }
 @Test void returnsEmptyForNoHoldingsAndDoesNotFabricateParents(){ assertThat(builder.build(normalized(List.of(source("A","1",1)),List.of(fund("F",1)),List.of())).displayAccounts()).isEmpty(); }
 @Test void rejectsNullSentinelAndMissingSequences(){
  assertFailure(holding("A","1","F",null,"1")); assertFailure(holding("A","1","F","-1.0","1")); assertFailure(holding("A","1","F","1",null)); assertFailure(holding("A","1","F","1","-1"));
  assertThatThrownBy(()->builder.build(normalized(List.of(source("A","1",null)),List.of(fund("F",1)),List.of(holding("A","1","F","1","1"))))).isInstanceOf(AccountBalanceRetrievalException.class);
  assertThatThrownBy(()->builder.build(normalized(List.of(source("A","1",1)),List.of(fund("F",null)),List.of(holding("A","1","F","1","1"))))).isInstanceOf(AccountBalanceRetrievalException.class);
 }
 @Test void preservesZeroScaleAndImmutableLists(){ var r=builder.build(normalized(List.of(source("A","1",1)),List.of(fund("F",1)),List.of(holding("A","1","F","0.00","0.000")))); var f=r.displayAccounts().getFirst().funds().getFirst(); assertThat(f.unitBalance().scale()).isEqualTo(2); assertThat(f.accountBalance().scale()).isEqualTo(3); assertThatThrownBy(()->r.displayAccounts().add(null)).isInstanceOf(UnsupportedOperationException.class); }
 private void assertFailure(RawAccountBalanceHolding h){ assertThatThrownBy(()->builder.build(normalized(List.of(source("A","1",1)),List.of(fund("F",1)),List.of(h)))).isInstanceOf(AccountBalanceRetrievalException.class); }
 private static NormalizedAccountBalanceData normalized(List<RawAccountBalanceDisplaySource>s,List<RawAccountBalanceFundDetail>f,List<RawAccountBalanceHolding>h){ Map<DisplaySourceKey,RawAccountBalanceDisplaySource> sm=new LinkedHashMap<>(); s.forEach(x->sm.put(new DisplaySourceKey(x.displaySource(),x.subDisplaySource()),x)); Map<FundKey,RawAccountBalanceFundDetail> fm=new LinkedHashMap<>(); f.forEach(x->fm.put(new FundKey(x.fundCode()),x)); Map<HoldingKey,RawAccountBalanceHolding> hm=new LinkedHashMap<>(); h.forEach(x->hm.put(new HoldingKey(x.displaySource(),x.subDisplaySource(),x.fundCode()),x)); return new NormalizedAccountBalanceData(null,null,sm,fm,hm); }
 private static RawAccountBalanceDisplaySource source(String d,String s,Integer q){ return new RawAccountBalanceDisplaySource(d,"E","中",s,"SE","中",q); }
 private static RawAccountBalanceFundDetail fund(String c,Integer q){ return new RawAccountBalanceFundDetail(c,"N","中",q,BigDecimal.ONE,"HKD",BigDecimal.ONE,"raw"); }
 private static RawAccountBalanceHolding holding(String d,String s,String f,String u,String n){ return new RawAccountBalanceHolding(d,s,f,u==null?null:new BigDecimal(u),n==null?null:new BigDecimal(n),null,null); }
}

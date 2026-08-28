package com.bct.ngtpa.apiservice.config.accountbalance;
import com.bct.ngtpa.apiservice.application.port.in.*; import com.bct.ngtpa.apiservice.application.port.out.*; import com.bct.ngtpa.apiservice.application.service.accountbalance.*; import com.bct.ngtpa.apiservice.application.usecase.GetAccountBalanceService; import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods=false) public class AccountBalanceApplicationConfig {
 @Bean GetAccountBalanceUseCase getAccountBalanceUseCase(ReferenceDatePort d,GeneralMetadataResolver m,AccountBalanceSourcePort s,AccountBalanceAssembler a){return new GetAccountBalanceService(d,m,s,a);}
 @Bean AccountBalanceNormalizer accountBalanceNormalizer(){return new DefaultAccountBalanceNormalizer();}
 @Bean AccountOverviewAggregator accountOverviewAggregator(){return new DefaultAccountOverviewAggregator();}
 @Bean FundLevelPortfolioBuilder fundLevelPortfolioBuilder(){return new DefaultFundLevelPortfolioBuilder();}
 @Bean SubDisplayLevelPortfolioBuilder subDisplayLevelPortfolioBuilder(){return new DefaultSubDisplayLevelPortfolioBuilder();}
 @Bean AccountBalancePresentationTransformer accountBalancePresentationTransformer(){return new DefaultAccountBalancePresentationTransformer();}
 @Bean AccountBalanceCurrencySelectionMapper accountBalanceCurrencySelectionMapper(){return new AccountBalanceCurrencySelectionMapper();}
 @Bean AccountBalanceFactsheetResolver accountBalanceFactsheetResolver(AccountBalancePresentationEventPort e){return new AccountBalanceFactsheetResolver(e);}
 @Bean AccountBalanceAssembler accountBalanceAssembler(AccountBalanceNormalizer n,AccountOverviewAggregator o,FundLevelPortfolioBuilder f,SubDisplayLevelPortfolioBuilder s,AccountBalancePresentationTransformer p,AccountBalanceCurrencySelectionMapper c,AccountBalanceFactsheetResolver u){return new DefaultAccountBalanceAssembler(n,o,f,s,p,c,u);}
}

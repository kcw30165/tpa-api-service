package com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(AccountBalanceApimProperties.class)
public class AccountBalanceApimValidationConfig { public AccountBalanceApimValidationConfig(AccountBalanceApimProperties properties){properties.validate();} }

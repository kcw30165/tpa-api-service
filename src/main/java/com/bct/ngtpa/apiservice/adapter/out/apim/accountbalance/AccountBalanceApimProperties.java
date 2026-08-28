package com.bct.ngtpa.apiservice.adapter.out.apim.accountbalance;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
@Getter @Setter
@ConfigurationProperties(prefix="apim.account-balance")
public class AccountBalanceApimProperties {
 private String path;
 public void validate(){if(path==null||path.isBlank())throw new IllegalStateException("apim.account-balance.path must not be blank");path=path.trim();}
}

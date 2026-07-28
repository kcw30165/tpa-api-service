package com.bct.ngtpa.apiservice.adapter.in.web.auth.config;
import lombok.Getter;import lombok.Setter;
@Getter @Setter public class LoginAccountReferenceProperties {
 private String prefix="acc";private int entropyBytes;private int maxGenerationAttempts;
 public void validate(){if(prefix==null||!prefix.matches("[A-Za-z][A-Za-z0-9_-]{1,15}"))throw new IllegalStateException("Invalid login account-ref prefix");if(entropyBytes<16)throw new IllegalStateException("login account-ref entropy-bytes must be at least 16");if(maxGenerationAttempts<=0)throw new IllegalStateException("login account-ref max-generation-attempts must be positive");}
}

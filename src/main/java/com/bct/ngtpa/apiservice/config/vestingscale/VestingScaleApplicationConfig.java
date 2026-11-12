package com.bct.ngtpa.apiservice.config.vestingscale;

import com.bct.ngtpa.apiservice.application.port.in.GetVestingScaleUseCase;
import com.bct.ngtpa.apiservice.application.port.out.VestingScalePort;
import com.bct.ngtpa.apiservice.application.usecase.vestingscale.GetVestingScaleService;
import com.bct.ngtpa.apiservice.application.usecase.vestingscale.VestingScaleVisibilityPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class VestingScaleApplicationConfig {

  @Bean
  GetVestingScaleUseCase getVestingScaleUseCase(VestingScalePort vestingScalePort) {
    return new GetVestingScaleService(vestingScalePort, new VestingScaleVisibilityPolicy());
  }
}

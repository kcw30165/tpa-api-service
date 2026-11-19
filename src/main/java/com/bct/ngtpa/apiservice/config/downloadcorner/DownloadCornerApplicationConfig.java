package com.bct.ngtpa.apiservice.config.downloadcorner;

import com.bct.ngtpa.apiservice.application.port.in.GetDownloadCornerUseCase;
import com.bct.ngtpa.apiservice.application.port.out.DownloadCornerPort;
import com.bct.ngtpa.apiservice.application.usecase.downloadcorner.GetDownloadCornerService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DownloadCornerApplicationConfig {

  @Bean
  GetDownloadCornerUseCase getDownloadCornerUseCase(DownloadCornerPort downloadCornerPort) {
    return new GetDownloadCornerService(downloadCornerPort);
  }
}

package com.bct.ngtpa.apiservice.adapter.out.sms.config;

import com.bct.ngtpa.apiservice.adapter.out.sms.SmsSenderAdapter;
import com.bct.ngtpa.apiservice.application.port.out.SmsSenderPort;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SmsProperties.class)
@ConditionalOnProperty(name = "sms.enabled", havingValue = "true", matchIfMissing = true)
public class SmsWebClientConfig {

  @Bean
  public WebClient smsWebClient(WebClient.Builder webClientBuilder, SmsProperties smsProperties) {
    HttpClient httpClient =
        HttpClient.create().responseTimeout(Duration.ofMillis(smsProperties.getTimeoutMillis()));

    return webClientBuilder
        .clone()
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .baseUrl(smsProperties.getBaseUrl())
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  @Bean
  public SmsSenderPort smsSenderPort(WebClient smsWebClient, SmsProperties smsProperties) {
    return new SmsSenderAdapter(smsWebClient, smsProperties);
  }

  @Bean
  public com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryTimeoutBound
      smsMessageDeliveryTimeoutBound(SmsProperties smsProperties) {
    return new com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryTimeoutBound(
        com.bct.ngtpa.apiservice.application.dto.message.MessageDeliveryChannel.SMS,
        smsProperties.getTimeoutMillis());
  }
}

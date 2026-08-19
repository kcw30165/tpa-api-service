package com.bct.ngtpa.apiservice.adapter.out.sms.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class SmsWebClientConfigTest {

  @Test
  void createsSmsWebClientUsingConfiguredBaseUrl() {
    SmsProperties properties = new SmsProperties();
    properties.setBaseUrl("https://sms.example.test");
    properties.setTimeoutMillis(5000);

    WebClient webClient = new SmsWebClientConfig().smsWebClient(WebClient.builder(), properties);
    AtomicReference<String> capturedUrl = new AtomicReference<>();
    ExchangeFunction exchangeFunction =
        request -> {
          capturedUrl.set(request.url().toString());
          return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        };

    webClient
        .mutate()
        .exchangeFunction(exchangeFunction)
        .build()
        .post()
        .uri("/sms/ext/sendSingleSMS.do")
        .retrieve()
        .toBodilessEntity()
        .block();

    assertThat(capturedUrl.get()).isEqualTo("https://sms.example.test/sms/ext/sendSingleSMS.do");
  }
}

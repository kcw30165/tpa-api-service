package com.bct.ngtpa.apiservice.service;

import static org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId;

import com.bct.ngtpa.apiservice.dto.apim.AccountBalanceRequest;
import com.bct.ngtpa.apiservice.dto.apim.AccountBalanceResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class ApimClientService {

    private final WebClient apimWebClient;

    public ApimClientService(WebClient apimWebClient) {
        this.apimWebClient = apimWebClient;
    }

    public Mono<AccountBalanceResponse> getAccountBalance(AccountBalanceRequest accountBalanceRequest) {
        return apimWebClient.post()
                .uri(uriBuilder -> uriBuilder.path("TRPGETEEBal").build())
                .attributes(clientRegistrationId("apim-client"))
                .bodyValue(accountBalanceRequest)
                .retrieve()
                .bodyToMono(AccountBalanceResponse.class);
    }


}

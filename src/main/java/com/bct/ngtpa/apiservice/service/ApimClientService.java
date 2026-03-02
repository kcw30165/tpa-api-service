package com.bct.ngtpa.apiservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.bct.ngtpa.apiservice.dto.apim.AccountBalanceResponse;
import com.bct.ngtpa.apiservice.dto.apim.AccountBalanceRequest;
import reactor.core.publisher.Mono;

@Service
public class ApimClientService {

    private static final Logger logger = LoggerFactory.getLogger(ApimClientService.class);
    private final WebClient apimWebClient;

    public ApimClientService(WebClient apimWebClient) {
        this.apimWebClient = apimWebClient;
    }

    public Mono<AccountBalanceResponse> getAccountBalance(AccountBalanceRequest accountBalanceRequest) {
        return apimWebClient.post()
                .uri(uriBuilder -> uriBuilder.path("TRPGETEEBal").build())
                .bodyValue(accountBalanceRequest)
                .retrieve()
                .bodyToMono(AccountBalanceResponse.class);
    }


}

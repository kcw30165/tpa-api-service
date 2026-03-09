package com.bct.ngtpa.apiservice.service;

import static org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId;

import com.bct.ngtpa.apiservice.dto.apim.AccountBalanceRequest;
import com.bct.ngtpa.apiservice.dto.apim.AccountBalanceResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class ApimClientService {
    private static final String ACCOUNT_BALANCE_API_NAME = "account-balance";

    private final WebClient apimWebClient;
    private final ApimPayloadCryptoService apimPayloadCryptoService;

    public ApimClientService(WebClient apimWebClient, ApimPayloadCryptoService apimPayloadCryptoService) {
        this.apimWebClient = apimWebClient;
        this.apimPayloadCryptoService = apimPayloadCryptoService;
    }

    public Mono<AccountBalanceResponse> getAccountBalance(AccountBalanceRequest accountBalanceRequest) {
        AccountBalanceRequest encryptedRequest = apimPayloadCryptoService.encryptRequest(
                ACCOUNT_BALANCE_API_NAME,
                accountBalanceRequest,
                AccountBalanceRequest.class);

        return apimWebClient.post()
                .uri(uriBuilder -> uriBuilder.path("TRPGetEEBal").build())
                .attributes(clientRegistrationId("apim-client"))
                .bodyValue(encryptedRequest)
                .retrieve()
                .bodyToMono(String.class)
                .map(responseBody -> apimPayloadCryptoService.decryptResponse(
                        ACCOUNT_BALANCE_API_NAME,
                        responseBody,
                        AccountBalanceResponse.class));
    }


}

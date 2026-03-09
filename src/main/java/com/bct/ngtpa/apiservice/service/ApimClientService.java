package com.bct.ngtpa.apiservice.service;

import static org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction.clientRegistrationId;

import com.bct.ngtpa.apiservice.dto.apim.AccountBalanceRequest;
import com.bct.ngtpa.apiservice.dto.apim.AccountBalanceResponse;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ApimClientService {
    private static final String ACCOUNT_BALANCE_API_NAME = "account-balance";
    private final ObjectMapper objectMapper;
    private final WebClient apimWebClient;
    private final ApimPayloadCryptoService apimPayloadCryptoService;

    public ApimClientService(WebClient apimWebClient, ApimPayloadCryptoService apimPayloadCryptoService, ObjectMapper objectMapper) {
        this.apimWebClient = apimWebClient;
        this.apimPayloadCryptoService = apimPayloadCryptoService;
        this.objectMapper = objectMapper;
    }

    public Mono<AccountBalanceResponse> getAccountBalance(AccountBalanceRequest accountBalanceRequest) {
        AccountBalanceRequest encryptedRequest = apimPayloadCryptoService.encryptRequest(
                ACCOUNT_BALANCE_API_NAME,
                accountBalanceRequest,
                AccountBalanceRequest.class);
                try {
                    log.info("APIM request body={}", objectMapper.writeValueAsString(encryptedRequest));
                } catch (Exception e) {
                    log.error("Failed to convert request body to JSON", e);
                }


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

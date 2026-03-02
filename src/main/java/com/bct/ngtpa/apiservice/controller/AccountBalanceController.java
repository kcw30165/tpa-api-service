package com.bct.ngtpa.apiservice.controller;

import com.bct.ngtpa.apiservice.dto.apim.AccountBalanceRequest;
import com.bct.ngtpa.apiservice.dto.apim.AccountBalanceResponse;
import com.bct.ngtpa.apiservice.service.ApimClientService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/account-balances")
public class AccountBalanceController {

    private final ApimClientService apimClientService;

    public AccountBalanceController(ApimClientService apimClientService) {
        this.apimClientService = apimClientService;
    }

    @PostMapping(
            value = "/query",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<AccountBalanceResponse> getAccountBalance(
            @Valid @RequestBody AccountBalanceRequest accountBalanceRequest) {
        return apimClientService.getAccountBalance(accountBalanceRequest);
    }
}

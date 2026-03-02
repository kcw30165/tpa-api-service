package com.bct.ngtpa.apiservice.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import com.bct.ngtpa.apiservice.dto.apim.AccountBalanceResponse;
import com.bct.ngtpa.apiservice.service.ApimClientService;
import com.bct.ngtpa.apiservice.dto.apim.AccountBalanceRequest;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/account-balance")
public class AccountBalanceController {
    private final ApimClientService apimClientService;
    public AccountBalanceController(ApimClientService apimClientService) {
        this.apimClientService = apimClientService;
    }

    @PostMapping("")
    public Mono<AccountBalanceResponse> getAccountBalance(@RequestBody AccountBalanceRequest accountBalanceRequest) {
        return apimClientService.getAccountBalance(accountBalanceRequest);
    }
}

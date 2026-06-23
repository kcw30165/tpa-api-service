package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import com.bct.ngtpa.apiservice.adapter.in.web.request.RefreshReferenceDateRequest;
import com.bct.ngtpa.apiservice.adapter.in.web.response.RefreshReferenceDateResponse;
import com.bct.ngtpa.apiservice.application.dto.RefreshReferenceDateCommand;
import com.bct.ngtpa.apiservice.application.port.in.RefreshReferenceDateUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/internal/reference-date")
@RequiredArgsConstructor
public class ReferenceDateController {

    private final RefreshReferenceDateUseCase refreshReferenceDateUseCase;

    @PostMapping(value = {"/refresh"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<RefreshReferenceDateResponse> refreshReferenceDate(@Valid @RequestBody RefreshReferenceDateRequest request) {
        return refreshReferenceDateUseCase.execute(new RefreshReferenceDateCommand(request.accountEnv()))
                .map(result -> new RefreshReferenceDateResponse(
                        result.accountEnv(),
                        result.refDate(),
                        result.redisUpdated()));
    }
}
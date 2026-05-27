package com.bct.ngtpa.apiservice.application.port.out;

import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface ApimReferenceDateRefreshPort {

    Mono<LocalDate> fetchReferenceDate(String accountEnv);
}
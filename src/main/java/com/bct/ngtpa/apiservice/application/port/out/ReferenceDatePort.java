package com.bct.ngtpa.apiservice.application.port.out;

import java.time.LocalDate;
import reactor.core.publisher.Mono;

public interface ReferenceDatePort {

  Mono<LocalDate> resolveReferenceDate(String accountEnv);
}

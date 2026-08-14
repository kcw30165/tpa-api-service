package com.bct.ngtpa.apiservice.adapter.out.apim.execution;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import reactor.core.publisher.Mono;

/** Typed APIM transport boundary. Stage 02 intentionally provides no implementation. */
public interface ApimExchangeExecutor {
    <R, D> Mono<ApimResponseEnvelope<D>> postEnvelope(ApimExchangeRequest<R, D> request);
}

package com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery;

import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.dto.TrpSearchMemberApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.mapper.TrpSearchMemberApimMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.mapper.TrpSearchMemberApimResultMapper;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupResult;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryLookupPort;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

/**
 * APIM outbound adapter for the Username Recovery {@code TRPSearchMember} member lookup.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-apim-integration-design.md}
 * section 8 (downstream mapping matrix) and section 9 (transport and security; reuse
 * certificate, encryption, request ID, timeout, logging, and retry components; retry only
 * according to the transport-owned retry policy).
 *
 * <p>The adapter delegates the raw APIM exchange to {@link TrpSearchMemberTransport} and owns
 * only application request/result mapping and operation-specific failure normalization. Only
 * {@code X-Request-Id} is read from the Reactor Context and passed to the transport separately
 * from credential resolution. Mapper outcomes {@code MATCHED},
 * {@code NOT_FOUND}, and {@code CONTRACT_INVALID} pass through; timeout/connection/5xx,
 * decryption, and invalid-envelope failures normalize to {@code UNAVAILABLE}; an unclassifiable
 * nonblank APIM err-message (represented by {@link TrpSearchMemberApimDownstreamException})
 * normalizes to {@code UNKNOWN_OUTCOME}. Downstream rejections are never exposed to the
 * application or frontend. Sensitive request/response values and raw downstream codes never
 * appear in messages or logs.
 */
@Component
public final class TrpSearchMemberAdapter implements UsernameRecoveryLookupPort {


    private final TrpSearchMemberTransport transport;
    private final TrpSearchMemberApimMapper requestMapper;
    private final TrpSearchMemberApimResultMapper resultMapper;

    public TrpSearchMemberAdapter(TrpSearchMemberTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.requestMapper = new TrpSearchMemberApimMapper();
        this.resultMapper = new TrpSearchMemberApimResultMapper();
    }

    @Override
    public Mono<UsernameRecoveryLookupResult> search(UsernameRecoveryLookupCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        TrpSearchMemberApimRequest request = requestMapper.toApimRequest(command);
        return Mono.deferContextual(contextView -> {
                    String requestId = contextView.getOrDefault(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, null);
                    return transport.post(request, requestId);
                })
                .map(this::toResult)
                .onErrorResume(TrpSearchMemberApimDownstreamException.class,
                        exception -> Mono.just(unknownOutcome()))
                .onErrorResume(ApimException.class, exception -> Mono.just(unavailable()))
                .onErrorResume(ApimCryptoException.class, exception -> Mono.just(unavailable()))
                .onErrorResume(WebClientRequestException.class, exception -> Mono.just(unavailable()))
                .onErrorResume(TimeoutException.class, exception -> Mono.just(unavailable()));
    }

    private UsernameRecoveryLookupResult toResult(ApimResponseEnvelope<TrpSearchMemberApimDataItem> envelope) {
        if (envelope == null || envelope.getResponse() == null) {
            return unavailable();
        }
        var response = envelope.getResponse();
        List<TrpSearchMemberApimDataItem> data = response.getData() == null
                ? List.of()
                : List.copyOf(response.getData());
        return resultMapper.toResult(response.getErrMessage(), data);
    }

    private static UsernameRecoveryLookupResult unavailable() {
        return new UsernameRecoveryLookupResult(UsernameRecoveryLookupResult.Status.UNAVAILABLE, List.of());
    }

    private static UsernameRecoveryLookupResult unknownOutcome() {
        return new UsernameRecoveryLookupResult(UsernameRecoveryLookupResult.Status.UNKNOWN_OUTCOME, List.of());
    }
}

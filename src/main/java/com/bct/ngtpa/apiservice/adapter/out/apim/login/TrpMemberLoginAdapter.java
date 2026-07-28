package com.bct.ngtpa.apiservice.adapter.out.apim.login;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.login.dto.TrpMemberLoginApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationResult;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationStatus;
import com.bct.ngtpa.apiservice.application.port.out.CredentialAuthenticationPort;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class TrpMemberLoginAdapter implements CredentialAuthenticationPort {
    private static final String WRONG_PASSWORD = "err.tpa.wrong.password";
    private static final String USERNAME_NOT_REGISTERED = "err.tpa.cust.id.not.registered";
    private static final String NO_VALID_ACCOUNT = "err.tpa.no.valid.account";
    private static final String ACCOUNT_LOCKED = "err.tpa.account.locked";

    private final TrpMemberLoginTransport transport;
    private final TrpMemberLoginContractMapper mapper;

    public TrpMemberLoginAdapter(
            TrpMemberLoginTransport transport,
            TrpMemberLoginContractMapper mapper) {
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    @Override
    public Mono<CredentialAuthenticationResult> authenticate(CredentialAuthenticationCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return transport.post(mapper.toRequest(command))
                .map(envelope -> mapEnvelope(command.username(), envelope))
                .onErrorResume(IllegalStateException.class,
                        ignored -> Mono.just(mapper.failure(CredentialAuthenticationStatus.UNAVAILABLE)));
    }

    private CredentialAuthenticationResult mapEnvelope(
            String normalizedUsername,
            ApimResponseEnvelope<TrpMemberLoginApimDataItem> envelope) {
        if (envelope == null || envelope.getResponse() == null) {
            return mapper.failure(CredentialAuthenticationStatus.UNAVAILABLE);
        }
        ApimResponseBody<TrpMemberLoginApimDataItem> response = envelope.getResponse();
        String error = normalize(response.getErrMessage());
        List<TrpMemberLoginApimDataItem> data = response.getData() == null
                ? List.of()
                : List.copyOf(response.getData());

        if (error != null) {
            if (!data.isEmpty()) {
                return mapper.failure(CredentialAuthenticationStatus.UNAVAILABLE);
            }
            return mapper.failure(statusFor(error));
        }
        if (data.size() != 1) {
            return mapper.failure(CredentialAuthenticationStatus.UNAVAILABLE);
        }
        TrpMemberLoginApimDataItem item = data.get(0);
        if (item == null) {
            return mapper.failure(CredentialAuthenticationStatus.UNAVAILABLE);
        }
        if (!item.success()) {
            // TRPMemberLogin represents an incorrect password as a single
            // unsuccessful data item with fail-count and no envelope error.
            // Explicit envelope errors above remain authoritative for unknown
            // username, invalid account, and account lock outcomes.
            return mapper.credentialFailure(
                    CredentialAuthenticationStatus.INCORRECT_PASSWORD,
                    item.failCount());
        }
        return mapper.toAuthenticated(normalizedUsername, item);
    }

    private CredentialAuthenticationStatus statusFor(String error) {
        return switch (error) {
            case WRONG_PASSWORD -> CredentialAuthenticationStatus.INCORRECT_PASSWORD;
            case USERNAME_NOT_REGISTERED -> CredentialAuthenticationStatus.INCORRECT_USERNAME;
            case NO_VALID_ACCOUNT -> CredentialAuthenticationStatus.INVALID_ACCOUNT;
            case ACCOUNT_LOCKED -> CredentialAuthenticationStatus.ACCOUNT_LOCKED;
            default -> CredentialAuthenticationStatus.UNAVAILABLE;
        };
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}

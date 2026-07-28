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
        List<TrpMemberLoginApimDataItem> data = response.getData();
        String error = normalize(response.getErrMessage());

        // Known APIM business errors are authoritative even if APIM also
        // returns one success=false item carrying fail-count.
        CredentialAuthenticationStatus businessStatus = statusFor(error);
        if (businessStatus != CredentialAuthenticationStatus.UNAVAILABLE) {
            if (data == null || data.isEmpty()) {
                return mapper.credentialFailure(businessStatus, null);
            }
            if (businessStatus == CredentialAuthenticationStatus.INCORRECT_PASSWORD
                    && data.size() == 1
                    && data.get(0) != null
                    && Boolean.FALSE.equals(data.get(0).success())) {
                return mapper.credentialFailure(businessStatus, data.get(0).failCount());
            }
            // Known error plus incompatible data is contradictory, not a safe
            // business rejection. Keep it as a technical/unavailable outcome.
            return mapper.failure(CredentialAuthenticationStatus.UNAVAILABLE);
        }
        // Unknown nonblank downstream errors remain technical failures.
        if (error != null) {
            return mapper.failure(CredentialAuthenticationStatus.UNAVAILABLE);
        }
        if (data == null || data.size() != 1 || data.get(0) == null) {
            return mapper.failure(CredentialAuthenticationStatus.UNAVAILABLE);
        }
        TrpMemberLoginApimDataItem item = data.get(0);
        if (Boolean.FALSE.equals(item.success())) {
            return mapper.credentialFailure(
                    CredentialAuthenticationStatus.INCORRECT_PASSWORD,
                    item.failCount());
        }
        if (!Boolean.TRUE.equals(item.success())) {
            return mapper.failure(CredentialAuthenticationStatus.UNAVAILABLE);
        }
        return mapper.toAuthenticated(normalizedUsername, item);
    }



    private CredentialAuthenticationStatus statusFor(String error) {
        if (error == null) {
            return CredentialAuthenticationStatus.UNAVAILABLE;
        }
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

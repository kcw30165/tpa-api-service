package com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth;

import com.bct.ngtpa.apiservice.application.dto.auth.login.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.*;
import com.bct.ngtpa.apiservice.application.port.out.LoginDestinationIdGenerator;
import java.time.Instant;
import java.util.*;

public final class LoginPreAuthContextFactory {
    private static final String WORKFLOW_VERSION = "v1";
    private final LoginDestinationIdGenerator ids;
    private final LoginDestinationMasker masker;

    public LoginPreAuthContextFactory(LoginDestinationIdGenerator ids, LoginDestinationMasker masker) {
        this.ids = Objects.requireNonNull(ids);
        this.masker = Objects.requireNonNull(masker);
    }

    public LoginPreAuthContext create(CredentialAuthenticationResult result, Instant verifiedAt) {
        Objects.requireNonNull(result);
        Objects.requireNonNull(verifiedAt);
        if (result.status() != CredentialAuthenticationStatus.AUTHENTICATED) {
            throw new IllegalArgumentException("Only authenticated credentials can create pre-auth context");
        }
        if (result.destinations().isEmpty())
            throw new IllegalStateException("Login requires at least one OTP destination");
        Map<String, LoginPreAuthDestination> destinations = new LinkedHashMap<>();
        for (LoginCredentialDestination source : result.destinations()) {
            String id = ids.generate();
            String publicChannel = "mobile".equals(source.channel()) ? "sms" : source.channel();
            var destination = new LoginPreAuthDestination(
                    id, publicChannel, source.destination(), masker.mask(source.channel(), source.destination()));
            if (destinations.putIfAbsent(id, destination) != null) {
                throw new IllegalStateException("Duplicate login destination ID generated");
            }
        }
        return new LoginPreAuthContext(result.normalizedUsername(), result.userRole(),
                destinations, verifiedAt, WORKFLOW_VERSION);
    }
}

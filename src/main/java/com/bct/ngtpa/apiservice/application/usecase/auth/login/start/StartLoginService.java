package com.bct.ngtpa.apiservice.application.usecase.auth.login.start;

import com.bct.ngtpa.apiservice.application.usecase.auth.login.replay.LoginStartReplayCoordinator;

import com.bct.ngtpa.apiservice.application.dto.auth.login.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.*;
import com.bct.ngtpa.apiservice.application.dto.session.SessionTtlPolicy;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.in.StartLoginUseCase;
import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;
import com.bct.ngtpa.apiservice.application.port.out.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency.*;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth.*;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.*;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.time.*;
import java.util.*;
import reactor.core.publisher.Mono;

public final class StartLoginService implements StartLoginUseCase {
    private final LoginCredentialPolicyService policy;
    private final LoginCredentialRequestHasher hasher;
    private final LoginCredentialIdempotencyCoordinator idempotency;
    private final CredentialAuthenticationPort credentials;
    private final LoginPreAuthContextFactory contexts;
    private final LoginPreAuthContextProtectorPort protector;
    private final AuthFlowSessionLifecycleService sessions;
    private final CreateAuthFlowSessionCsrfUseCase csrf;
    private final LoginStartReplayCoordinator replayCoordinator;
    private final Clock clock;
    private final int ttl;
    private final boolean required;
    private final String prefix;
    private final int entropy;

    public StartLoginService(LoginCredentialPolicyService policy, LoginCredentialRequestHasher hasher,
            LoginCredentialIdempotencyCoordinator idempotency, CredentialAuthenticationPort credentials,
            LoginPreAuthContextFactory contexts, LoginPreAuthContextProtectorPort protector,
            AuthFlowSessionLifecycleService sessions, CreateAuthFlowSessionCsrfUseCase csrf,
            LoginStartReplayCoordinator replayCoordinator, Clock clock, int ttl,
            boolean required, String prefix, int entropy) {
        this.policy = Objects.requireNonNull(policy);
        this.hasher = Objects.requireNonNull(hasher);
        this.idempotency = Objects.requireNonNull(idempotency);
        this.credentials = Objects.requireNonNull(credentials);
        this.contexts = Objects.requireNonNull(contexts);
        this.protector = Objects.requireNonNull(protector);
        this.sessions = Objects.requireNonNull(sessions);
        this.csrf = Objects.requireNonNull(csrf);
        this.replayCoordinator = Objects.requireNonNull(replayCoordinator);
        this.clock = Objects.requireNonNull(clock);
        this.ttl = ttl;
        this.required = required;
        this.prefix = prefix;
        this.entropy = entropy;
    }

    public Mono<StartLoginResult> start(StartLoginCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        CredentialAuthenticationCommand normalized = policy.validateAndNormalize(
                new CredentialAuthenticationCommand(
                        command.username(),
                        command.password(),
                        command.acceptLanguage(),
                        command.correlationId(), 
                        ActorUserRole.ROLE_USER_MBR));
        LoginIdempotencyKey key = new LoginIdempotencyKey(command.idempotencyKey());
        LoginCredentialRequestHash hash = hasher.hash(normalized);

        return idempotency.reserve(key, hash)
                .flatMap(reservation -> switch (reservation.outcome()) {
                    case STARTED -> authenticateAndEstablish(normalized)
                            .flatMap(result -> finalizeStarted(key, hash, result))
                            .onErrorResume(error -> replayCoordinator.markUnknown(key, hash)
                                    .onErrorResume(markError -> Mono.empty())
                                    .then(Mono.error(error)));
                    case COMPLETED_REPLAY -> replayCoordinator.replay(
                            reservation.record().replaySnapshot());
                    case DUPLICATE_IN_PROGRESS -> Mono.just(simple(
                            StartLoginStatus.DUPLICATE_IN_PROGRESS, null));
                    case KEY_REUSED_WITH_DIFFERENT_REQUEST -> Mono.just(simple(
                            StartLoginStatus.KEY_REUSED, null));
                    case FAILED_EXISTING -> Mono.just(simple(
                            StartLoginStatus.FAILED_EXISTING, null));
                    case UNKNOWN_OUTCOME_EXISTING -> Mono.just(simple(
                            StartLoginStatus.UNKNOWN_OUTCOME, null));
                });
    }

    private Mono<StartLoginResult> authenticateAndEstablish(CredentialAuthenticationCommand command) {
        return credentials.authenticate(command).flatMap(result -> {
            if (result.status() != CredentialAuthenticationStatus.AUTHENTICATED)
                return Mono.just(failure(result));
            LoginPreAuthContext context = contexts.create(result, clock.instant());
            LoginPreAuthEncryptedContext encrypted = protector.protect(context);
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("loginContext.keyVersion", encrypted.keyVersion());
            attributes.put("loginContext.algorithm", encrypted.algorithm());
            attributes.put("loginContext.iv", encrypted.iv());
            attributes.put("loginContext.ciphertext", encrypted.ciphertext());
            var data = new AuthFlowSessionData(null, null, attributes);
            var start = new StartAuthFlowSessionCommand(AuthFlowScenario.LOGIN, AuthFlowStatus.STARTED,
                    new SessionTtlPolicy(true, required, ttl), prefix, entropy, data, null, null,
                    command.correlationId());
            return sessions.start(start).flatMap(created -> csrf
                    .createForSession(new CreateAuthFlowSessionCsrfCommand(created.session().authFlowSessionId(),
                            clock.instant(), ttl, command.correlationId()))
                    .map(token -> new StartLoginResult(StartLoginStatus.STARTED,
                            created.session().authFlowSessionId().value(), token.rawToken(), created.expiresInSeconds(),
                            publicChoices(context), result.failCount(), null)));
        });
    }

    private Mono<StartLoginResult> finalizeStarted(
            LoginIdempotencyKey key,
            LoginCredentialRequestHash hash,
            StartLoginResult result) {
        if (result.status() == StartLoginStatus.STARTED) {
            return replayCoordinator.commit(key, hash, result);
        }
        if (result.status() == StartLoginStatus.UNAVAILABLE) {
            return replayCoordinator.markUnknown(key, hash).thenReturn(result);
        }
        return replayCoordinator.markRejected(key, hash, result.status()).thenReturn(result);
    }

    private List<LoginDestinationChoice> publicChoices(LoginPreAuthContext c) {
        return c.destinationsById().values().stream()
                .map(d -> new LoginDestinationChoice(d.destinationId(), d.channel(), d.maskedDestination())).toList();
    }

    private StartLoginResult failure(CredentialAuthenticationResult r) {
        StartLoginStatus s = switch (r.status()) {
            case INCORRECT_PASSWORD -> StartLoginStatus.INCORRECT_PASSWORD;
            case INCORRECT_USERNAME -> StartLoginStatus.INCORRECT_USERNAME;
            case INVALID_ACCOUNT -> StartLoginStatus.INVALID_ACCOUNT;
            case ACCOUNT_LOCKED -> StartLoginStatus.ACCOUNT_LOCKED;
            default -> StartLoginStatus.UNAVAILABLE;
        };
        return new StartLoginResult(s, null, null, null, List.of(), r.failCount(), null);
    }

    private StartLoginResult simple(StartLoginStatus s, String ref) {
        return new StartLoginResult(s, null, null, null, List.of(), null, ref);
    }
}

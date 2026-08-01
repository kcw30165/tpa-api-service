package com.bct.ngtpa.apiservice.application.usecase.session.authflow;

import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;

import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import java.time.Instant;
import java.util.Objects;
import reactor.core.publisher.Mono;

public class AuthFlowSessionCsrfService implements CreateAuthFlowSessionCsrfUseCase {
    private final AuthFlowSessionPort authFlowSessionPort;
    private final CsrfTokenGenerator csrfTokenGenerator;
    private final CsrfTokenValidationService csrfTokenValidationService;
    private final CsrfTokenHasher tokenBindingHasher;
    private final RevisionAwareAuthFlowSessionWriter revisionWriter;

    public AuthFlowSessionCsrfService(AuthFlowSessionPort authFlowSessionPort) {
        this(authFlowSessionPort, new SecureRandomCsrfTokenGenerator(), new Sha256CsrfTokenHasher());
    }

    public AuthFlowSessionCsrfService(AuthFlowSessionPort authFlowSessionPort, CsrfTokenGenerator csrfTokenGenerator,
            CsrfTokenHasher csrfTokenHasher) {
        this.authFlowSessionPort = Objects.requireNonNull(authFlowSessionPort, "authFlowSessionPort must not be null");
        this.csrfTokenGenerator = Objects.requireNonNull(csrfTokenGenerator, "csrfTokenGenerator must not be null");
        this.tokenBindingHasher = Objects.requireNonNull(csrfTokenHasher, "csrfTokenHasher must not be null");
        this.csrfTokenValidationService = new CsrfTokenValidationService(tokenBindingHasher);
        this.revisionWriter = new RevisionAwareAuthFlowSessionWriter(authFlowSessionPort);
    }

    @Override
    public Mono<CreateAuthFlowSessionCsrfResult> createForSession(CreateAuthFlowSessionCsrfCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return bindToken(command.authFlowSessionId(), command.now(), command.ttlSeconds());
    }

    public Mono<CreateAuthFlowSessionCsrfResult> rotateForSession(RotateAuthFlowSessionCsrfCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return bindToken(command.authFlowSessionId(), command.now(), command.ttlSeconds());
    }

    public Mono<ValidateAuthFlowSessionCsrfResult> validateForSession(ValidateAuthFlowSessionCsrfCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return authFlowSessionPort.load(command.authFlowSessionId())
                .map(optional -> {
                    if (optional.isEmpty()) {
                        return result(AuthFlowSessionCsrfValidationStatus.SESSION_MISSING);
                    }
                    AuthFlowSession session = optional.get();
                    if (session.isExpired(command.now())) {
                        return result(AuthFlowSessionCsrfValidationStatus.SESSION_EXPIRED);
                    }
                    if (session.isTerminal()) {
                        return result(AuthFlowSessionCsrfValidationStatus.SESSION_NOT_USABLE);
                    }
                    CsrfTokenValidationResult token = csrfTokenValidationService.validate(
                            command.submittedToken(), session.csrfTokenHash());
                    return result(switch (token.status()) {
                        case VALID -> AuthFlowSessionCsrfValidationStatus.VALID;
                        case TOKEN_MISSING -> AuthFlowSessionCsrfValidationStatus.TOKEN_MISSING;
                        case TOKEN_HASH_MISSING -> AuthFlowSessionCsrfValidationStatus.TOKEN_HASH_MISSING;
                        case TOKEN_MISMATCH -> AuthFlowSessionCsrfValidationStatus.TOKEN_MISMATCH;
                    });
                });
    }

    private Mono<CreateAuthFlowSessionCsrfResult> bindToken(AuthFlowSessionId authFlowSessionId, Instant now,
            int ttlSeconds) {
        return authFlowSessionPort.load(authFlowSessionId)
                .flatMap(optional -> optional
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new IllegalStateException("Auth flow session not available"))))
                .flatMap(session -> {
                    if (session.isExpired(now) || session.isTerminal()) {
                        return Mono.error(new IllegalStateException("Auth flow session is not usable"));
                    }
                    String rawToken = csrfTokenGenerator.generate();
                    String tokenHash = tokenBindingHasher.hash(rawToken);
                    AuthFlowSession updated = new AuthFlowSession(
                            session.authFlowSessionId(),
                            session.scenario(),
                            session.status(),
                            tokenHash,
                            session.activeOtpChallengeId(),
                            session.data(),
                            session.createdAt(),
                            now,
                            session.expiresAt());
                    return revisionWriter.updateExpected(session, updated)
                            .thenReturn(new CreateAuthFlowSessionCsrfResult(rawToken, now.plusSeconds(ttlSeconds),
                                    ttlSeconds));
                });
    }

    private static ValidateAuthFlowSessionCsrfResult result(AuthFlowSessionCsrfValidationStatus status) {
        return new ValidateAuthFlowSessionCsrfResult(status, status == AuthFlowSessionCsrfValidationStatus.VALID);
    }
}

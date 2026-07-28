package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.*;
import com.bct.ngtpa.apiservice.application.port.out.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthenticatedSessionLifecycleServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");
    private static final AuthenticatedSessionId ID = new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

    @Test
    void createGeneratesOpaqueIdProtectsAndStoresWithEarlierIdleTtl() {
        var f = new Fixture();
        var cmd = new CreateAuthenticatedSessionCommand(new ActorContext("member", "USER"), List.of(account()),
                "csrf_hash", "req");
        StepVerifier.create(f.service.create(cmd)).assertNext(r -> {
            assertThat(r.status()).isEqualTo(AuthenticatedSessionLifecycleStatus.ACTIVE);
            assertThat(r.session().authenticatedSessionId()).isEqualTo(ID);
            assertThat(r.session().idleExpiresAt()).isEqualTo(NOW.plusSeconds(1800));
            assertThat(r.session().absoluteExpiresAt()).isEqualTo(NOW.plusSeconds(28800));
        }).verifyComplete();
        assertThat(f.stored.get().ttl()).isEqualTo(Duration.ofSeconds(1800));
    }

    @Test
    void loadActiveTouchesIdleExpiryWithoutExtendingAbsoluteExpiry() {
        var f = new Fixture();
        var old = session(NOW.minusSeconds(120), NOW.plusSeconds(60), NOW.plusSeconds(600),
                AuthenticatedSessionStatus.ACTIVE);
        f.loaded.set(old);
        StepVerifier.create(f.service.loadAndTouch(new LoadAndTouchAuthenticatedSessionCommand(ID, "req")))
                .assertNext(r -> {
                    assertThat(r.status()).isEqualTo(AuthenticatedSessionLifecycleStatus.ACTIVE);
                    assertThat(r.session().lastAccessedAt()).isEqualTo(NOW);
                    assertThat(r.session().idleExpiresAt()).isEqualTo(NOW.plusSeconds(600));
                    assertThat(r.session().absoluteExpiresAt()).isEqualTo(NOW.plusSeconds(600));
                }).verifyComplete();
        assertThat(f.touched.get().ttl()).isEqualTo(Duration.ofSeconds(600));
    }

    @Test
    void missingExpiredAndRevokedSessionsDoNotTouch() {
        var missing = new Fixture();
        StepVerifier.create(missing.service.loadAndTouch(new LoadAndTouchAuthenticatedSessionCommand(ID, "req")))
                .assertNext(r -> assertThat(r.status()).isEqualTo(AuthenticatedSessionLifecycleStatus.NOT_FOUND))
                .verifyComplete();
        var expired = new Fixture();
        expired.loaded.set(session(NOW.minusSeconds(100), NOW.minusSeconds(1), NOW.plusSeconds(100),
                AuthenticatedSessionStatus.ACTIVE));
        StepVerifier.create(expired.service.loadAndTouch(new LoadAndTouchAuthenticatedSessionCommand(ID, "req")))
                .assertNext(r -> assertThat(r.status()).isEqualTo(AuthenticatedSessionLifecycleStatus.EXPIRED))
                .verifyComplete();
        var revoked = new Fixture();
        revoked.loaded.set(session(NOW.minusSeconds(100), NOW.plusSeconds(100), NOW.plusSeconds(100),
                AuthenticatedSessionStatus.REVOKED));
        StepVerifier.create(revoked.service.loadAndTouch(new LoadAndTouchAuthenticatedSessionCommand(ID, "req")))
                .assertNext(r -> assertThat(r.status()).isEqualTo(AuthenticatedSessionLifecycleStatus.REVOKED))
                .verifyComplete();
    }

    @Test
    void revokePersistsProtectedTerminalSession() {
        var fixture = new Fixture();
        fixture.loaded.set(session(
                NOW.minusSeconds(100),
                NOW.plusSeconds(100),
                NOW.plusSeconds(1000),
                AuthenticatedSessionStatus.ACTIVE));

        StepVerifier.create(fixture.service.revoke(
                        new RevokeActiveAuthenticatedSessionCommand(
                                ID,
                                "SECURITY_REJECTION",
                                "req")))
                .assertNext(result -> assertThat(result.status())
                        .isEqualTo(AuthenticatedSessionLifecycleStatus.REVOKED))
                .verifyComplete();

        assertThat(fixture.loaded.get().status())
                .isEqualTo(AuthenticatedSessionStatus.REVOKED);
        assertThat(fixture.touched.get()).isNotNull();
        assertThat(fixture.touched.get().ttl())
                .isEqualTo(Duration.ofSeconds(300));
    }

    private static AuthenticatedSessionAccount account() {
        return new AuthenticatedSessionAccount(new AuthenticatedAccountRef("acc_abcdefghijklmnopqrstuvwx"), "DB",
                "00000008802", "0001", "DBA", "OE", null, null);
    }

    private static AuthenticatedSession session(Instant last, Instant idle, Instant absolute,
            AuthenticatedSessionStatus status) {
        return new AuthenticatedSession(ID, new ActorContext("member", "USER"), List.of(account()), status, "csrf_hash",
                NOW.minusSeconds(200), last, idle, absolute);
    }

    static final class Fixture {
        final AtomicReference<StoreAuthenticatedSessionCommand> stored = new AtomicReference<>();
        final AtomicReference<TouchAuthenticatedSessionCommand> touched = new AtomicReference<>();
        final AtomicReference<AuthenticatedSession> loaded = new AtomicReference<>();
        final AuthenticatedSessionLifecycleService service;

        Fixture() {
            AuthenticatedSessionProtectorPort protector = new AuthenticatedSessionProtectorPort() {
                public ProtectedAuthenticatedSession protect(AuthenticatedSession s) {
                    loaded.set(s);
                    return com.bct.ngtpa.apiservice.application.dto.session.authenticated.ProtectedAuthenticatedSessionTestFixtures.envelope("v1", "cipher", "nonce", "tag");
                }

                public AuthenticatedSession unprotect(ProtectedAuthenticatedSession p) {
                    return loaded.get();
                }
            };
            AuthenticatedSessionPort port = new AuthenticatedSessionPort() {
                public Mono<AuthenticatedSessionPersistenceResult> store(StoreAuthenticatedSessionCommand c) {
                    stored.set(c);
                    return Mono.just(
                            new AuthenticatedSessionPersistenceResult(AuthenticatedSessionPersistenceStatus.STORED));
                }

                public Mono<Optional<ProtectedAuthenticatedSession>> load(LoadAuthenticatedSessionCommand c) {
                    return Mono.just(Optional.ofNullable(loaded.get())
                            .map(x -> com.bct.ngtpa.apiservice.application.dto.session.authenticated.ProtectedAuthenticatedSessionTestFixtures.envelope("v1", "cipher", "nonce", "tag")));
                }

                public Mono<AuthenticatedSessionPersistenceResult> touch(TouchAuthenticatedSessionCommand c) {
                    touched.set(c);
                    return Mono.just(
                            new AuthenticatedSessionPersistenceResult(AuthenticatedSessionPersistenceStatus.TOUCHED));
                }

                public Mono<AuthenticatedSessionPersistenceResult> revoke(RevokeAuthenticatedSessionCommand c) {
                    return Mono.just(
                            new AuthenticatedSessionPersistenceResult(AuthenticatedSessionPersistenceStatus.REVOKED));
                }

                public Mono<AuthenticatedSessionPersistenceResult> delete(DeleteAuthenticatedSessionCommand c) {
                    return Mono.just(
                            new AuthenticatedSessionPersistenceResult(AuthenticatedSessionPersistenceStatus.DELETED));
                }
            };
            service = new AuthenticatedSessionLifecycleService(port, protector, () -> ID, Duration.ofSeconds(1800),
                    Duration.ofSeconds(28800),
            Duration.ofSeconds(300), Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }
}

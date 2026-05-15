package com.bct.ngtpa.apiservice.application.port.out;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.MemberOwnerContext;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Contract test for {@link PortalAccessContextPort}.
 *
 * <p>Verifies the port method signature and that the port can be implemented as a
 * functional interface. The transitional method uses {@code accountRef} as the
 * account-selection key. Until auth-server integration is complete, implementations
 * are expected to resolve context from externalized configuration keyed by
 * {@code accountRef}; callers must document which accountRef value they supply.
 */
class PortalAccessContextPortTest {

    private static final PortalAccessContext STUB_CONTEXT = new PortalAccessContext(
            new ActorContext("staff-01", "STAFF", "RM"),
            new MemberOwnerContext("member-99", "MBR"),
            new AccountContext("ACC-001", "JP", "POL-001", "CERT-001", "JPM", "OE"));

    // ---------------------------------------------------------------------------
    // Port contract: functional-interface impl
    // ---------------------------------------------------------------------------

    @Test
    void portCanBeImplementedAsLambda() {
        // Arrange: minimal lambda implementation
        PortalAccessContextPort port = accountRef -> reactor.core.publisher.Mono.just(STUB_CONTEXT);

        // Act + Assert
        StepVerifier.create(port.resolvePortalAccessContext("ACC-001"))
                .expectNext(STUB_CONTEXT)
                .verifyComplete();
    }

    @Test
    void portReceivesAccountRefAndReturnsMono() {
        // Capture the accountRef the port was called with
        var capturedRef = new java.util.concurrent.atomic.AtomicReference<String>();

        PortalAccessContextPort port = accountRef -> {
            capturedRef.set(accountRef);
            return reactor.core.publisher.Mono.just(STUB_CONTEXT);
        };

        StepVerifier.create(port.resolvePortalAccessContext("ACC-001"))
                .expectNextMatches(ctx -> ctx.account().accountRef().equals("ACC-001")
                        || ctx.equals(STUB_CONTEXT))
                .verifyComplete();

        assertEquals("ACC-001", capturedRef.get(),
                "port must receive the accountRef supplied by the caller");
    }

    @Test
    void portPropagatesError() {
        PortalAccessContextPort port = accountRef ->
                reactor.core.publisher.Mono.error(
                        new RuntimeException("no context for: " + accountRef));

        StepVerifier.create(port.resolvePortalAccessContext("UNKNOWN"))
                .expectErrorMatches(ex -> ex instanceof RuntimeException
                        && ex.getMessage().contains("UNKNOWN"))
                .verify();
    }
}

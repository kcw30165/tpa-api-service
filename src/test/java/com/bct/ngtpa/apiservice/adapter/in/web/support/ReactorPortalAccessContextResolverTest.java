package com.bct.ngtpa.apiservice.adapter.in.web.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.exception.PortalAccessContextResolutionException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.PortalAccessContextKeys;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ReactorCurrentPortalAccessContextProviderTest {

    private final ReactorCurrentPortalAccessContextProvider resolver = new ReactorCurrentPortalAccessContextProvider();

    @Test
    void currentReturnsPortalAccessContextFromReactorContext() {
        PortalAccessContext expected = portalAccessContext("ACC-1", "JP", "JPM", "OE");

        StepVerifier.create(resolver.current()
                        .contextWrite(context -> context.put(PortalAccessContextKeys.CONTEXT_KEY, expected)))
                .assertNext(actual -> assertSame(expected, actual))
                .verifyComplete();
    }

    @Test
    void currentOrEmptyCompletesEmptyWhenContextIsMissing() {
        StepVerifier.create(resolver.currentOrEmpty())
                .verifyComplete();
    }

    @Test
    void currentFailsWithMemberContextInvalidWhenContextIsMissing() {
        StepVerifier.create(resolver.current())
                .expectErrorSatisfies(error -> {
                    PortalAccessContextResolutionException ex = assertInstanceOf(
                            PortalAccessContextResolutionException.class,
                            error);
                    assertEquals(ErrorCodes.MEMBER_CONTEXT_INVALID, ex.getErrorCode());
                    assertEquals("PortalAccessContext is not available in the current request context.", ex.getMessage());
                })
                .verify();
    }

    @Test
    void currentFailsClearlyWhenContextValueHasWrongType() {
        StepVerifier.create(resolver.current()
                        .contextWrite(context -> context.put(PortalAccessContextKeys.CONTEXT_KEY, "not-a-context")))
                .expectErrorSatisfies(error -> {
                    PortalAccessContextResolutionException ex = assertInstanceOf(
                            PortalAccessContextResolutionException.class,
                            error);
                    assertEquals(ErrorCodes.MEMBER_CONTEXT_INVALID, ex.getErrorCode());
                    assertEquals("PortalAccessContext value in the current request context is invalid.", ex.getMessage());
                })
                .verify();
    }

    @Test
    void currentContextIsPerSubscriberAndDoesNotLeakBetweenRequests() {
        PortalAccessContext first = portalAccessContext("ACC-1", "JP", "JPM", "OE");
        PortalAccessContext second = portalAccessContext("ACC-2", "DB", "", "");

        Mono<String> accountEnv = resolver.current()
                .map(context -> context.account().accountRef() + ":" + context.account().accountEnv());

        StepVerifier.create(accountEnv.contextWrite(context -> context.put(PortalAccessContextKeys.CONTEXT_KEY, first)))
                .expectNext("ACC-1:JP")
                .verifyComplete();

        StepVerifier.create(accountEnv.contextWrite(context -> context.put(PortalAccessContextKeys.CONTEXT_KEY, second)))
                .expectNext("ACC-2:DB")
                .verifyComplete();
    }

    private static PortalAccessContext portalAccessContext(
            String accountRef,
            String accountEnv,
            String trustCode,
            String schemeType) {
        return new PortalAccessContext(
                new ActorContext("actor-001", "MEMBER"),
                new AccountContext(
                        accountRef,
                        accountEnv,
                        "policy-001",
                        "cert-001",
                        trustCode,
                        schemeType,
                        null,
                        null));
    }
}

package com.bct.ngtpa.apiservice.adapter.in.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.logout.AuthenticatedLogoutWebMapper;
import com.bct.ngtpa.apiservice.application.usecase.auth.logout.AuthenticatedLogoutResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.logout.AuthenticatedLogoutUseCase;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthLogoutControllerTest {
    @Test
    void validSidInvokesLogoutAndReturnsGenericSuccess() {
        AtomicBoolean invoked = new AtomicBoolean();
        AuthenticatedLogoutUseCase useCase = command -> {
            invoked.set(true);
            assertThat(command.authenticatedSessionId().value())
                    .isEqualTo("sid_abcdefghijklmnopqrstuvwx");
            assertThat(command.correlationId()).isEqualTo("req-1");
            return Mono.just(AuthenticatedLogoutResult.loggedOut());
        };
        var controller = new AuthLogoutController(useCase, new AuthenticatedLogoutWebMapper());
        var response = new MockServerHttpResponse();

        StepVerifier.create(controller.logout(
                        "sid_abcdefghijklmnopqrstuvwx", "req-1", response))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(entity.getBody().success()).isTrue();
                    assertThat(entity.getBody().result()).isNull();
                })
                .verifyComplete();

        assertThat(invoked).isTrue();
        assertThat(response.getHeaders().getCacheControl()).isEqualTo(CacheControl.noStore().getHeaderValue());
    }

    @Test
    void missingOrMalformedSidIsIdempotentSuccessWithoutUseCaseCall() {
        AtomicBoolean invoked = new AtomicBoolean();
        AuthenticatedLogoutUseCase useCase = command -> {
            invoked.set(true);
            return Mono.error(new AssertionError("logout use case must not run"));
        };
        var controller = new AuthLogoutController(useCase, new AuthenticatedLogoutWebMapper());

        for (String sid : new String[] {null, " ", "invalid"}) {
            StepVerifier.create(controller.logout(sid, "req-1", new MockServerHttpResponse()))
                    .assertNext(entity -> {
                        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(entity.getBody().success()).isTrue();
                        assertThat(entity.getBody().result()).isNull();
                    })
                    .verifyComplete();
        }
        assertThat(invoked).isFalse();
    }

    @Test
    void loggedOutAndAlreadyLoggedOutHaveIdenticalPublicEnvelope() {
        var mapper = new AuthenticatedLogoutWebMapper();
        assertThat(mapper.toResponse(AuthenticatedLogoutResult.loggedOut()))
                .isEqualTo(mapper.toResponse(AuthenticatedLogoutResult.alreadyLoggedOut()));
    }

    @Test
    void failureReturnsGenericServiceUnavailableContract() {
        AuthenticatedLogoutUseCase useCase = command -> Mono.just(AuthenticatedLogoutResult.failed());
        var controller = new AuthLogoutController(useCase, new AuthenticatedLogoutWebMapper());

        StepVerifier.create(controller.logout(
                        "sid_abcdefghijklmnopqrstuvwx", "req-1", new MockServerHttpResponse()))
                .assertNext(entity -> {
                    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(entity.getBody().success()).isFalse();
                    assertThat(entity.getBody().errors()).singleElement()
                            .satisfies(error -> assertThat(error.code())
                                    .isEqualTo("err.system.unexpected"));
                })
                .verifyComplete();
    }
}

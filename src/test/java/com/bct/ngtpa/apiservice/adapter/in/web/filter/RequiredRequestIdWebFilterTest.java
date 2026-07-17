package com.bct.ngtpa.apiservice.adapter.in.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RequiredRequestIdWebFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RequiredRequestIdWebFilter filter = new RequiredRequestIdWebFilter(
            (code, locale, accountEnv, trustCode, schemeType) -> "resolved-" + code,
            objectMapper);

    @Test
    void missingRequestIdRejectsApiRequestBeforeBusinessChain() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/captcha/challenge").build());

        StepVerifier.create(filter.filter(exchange, ignored -> {
            invoked.set(true);
            return Mono.empty();
        })).verifyComplete();

        assertThat(invoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(400);
        JsonNode body = responseBody(exchange);
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("status").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(body.at("/errors/0/code").asText()).isEqualTo("err.request.id.missing");
        assertThat(body.at("/errors/0/message").asText()).isEqualTo("resolved-err.request.id.missing");
        assertThat(body.at("/errors/0/targets/0").asText()).isEqualTo("X-Request-Id");
    }

    @Test
    void blankRequestIdIsRejected() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/auth/captcha/challenge")
                .header(RequestCorrelation.REQUEST_ID_HEADER, "   ")
                .build());

        StepVerifier.create(filter.filter(exchange, ignored -> Mono.error(
                new AssertionError("business chain must not be called"))))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void suppliedRequestIdAllowsApiRequest() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/auth/captcha/challenge")
                .header(RequestCorrelation.REQUEST_ID_HEADER, "req-123")
                .build());

        StepVerifier.create(filter.filter(exchange, ignored -> {
            invoked.set(true);
            return Mono.empty();
        })).verifyComplete();

        assertThat(invoked).isTrue();
    }

    @Test
    void nonApiRequestIsNotSubjectToApplicationHeaderContract() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health").build());

        StepVerifier.create(filter.filter(exchange, ignored -> {
            invoked.set(true);
            return Mono.empty();
        })).verifyComplete();

        assertThat(invoked).isTrue();
    }

    private JsonNode responseBody(MockServerWebExchange exchange) {
        var buffers = exchange.getResponse().getBody();
        byte[] bytes = DataBufferUtils.join(buffers)
                .map(buffer -> {
                    byte[] value = new byte[buffer.readableByteCount()];
                    buffer.read(value);
                    DataBufferUtils.release(buffer);
                    return value;
                })
                .block();
        try {
            return objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}

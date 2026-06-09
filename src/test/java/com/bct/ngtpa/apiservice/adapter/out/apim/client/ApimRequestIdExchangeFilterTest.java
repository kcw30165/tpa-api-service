package com.bct.ngtpa.apiservice.adapter.out.apim.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

class ApimRequestIdExchangeFilterTest {

    private final ApimRequestIdExchangeFilter filter = new ApimRequestIdExchangeFilter();

    @Test
    void addsRequestIdHeaderWhenContextContainsRequestId() {
        ExchangeFilterFunction exchangeFilter = filter.filter();
        ClientRequest request = ClientRequest
                .create(HttpMethod.GET, URI.create("https://api.example.test/test"))
                .build();
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        exchangeFilter.filter(request, req -> {
            captured.set(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        })
        .contextWrite(ctx -> ctx.put(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, "test-request-id-123"))
        .block();

        assertNotNull(captured.get());
        List<String> requestIdHeader = captured.get().headers().get(RequestCorrelation.REQUEST_ID_HEADER);
        assertNotNull(requestIdHeader);
        assertEquals(List.of("test-request-id-123"), requestIdHeader);
    }

    @Test
    void doesNotAddRequestIdHeaderWhenContextLacksRequestId() {
        ExchangeFilterFunction exchangeFilter = filter.filter();
        ClientRequest request = ClientRequest
                .create(HttpMethod.GET, URI.create("https://api.example.test/test"))
                .build();
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        exchangeFilter.filter(request, req -> {
            captured.set(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        }).block();

        assertNotNull(captured.get());
        assertNull(captured.get().headers().get(RequestCorrelation.REQUEST_ID_HEADER));
    }

    @Test
    void doesNotAddRequestIdHeaderWhenContextRequestIdIsBlank() {
        ExchangeFilterFunction exchangeFilter = filter.filter();
        ClientRequest request = ClientRequest
                .create(HttpMethod.GET, URI.create("https://api.example.test/test"))
                .build();
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        exchangeFilter.filter(request, req -> {
            captured.set(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        })
        .contextWrite(ctx -> ctx.put(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, "   "))
        .block();

        assertNotNull(captured.get());
        assertNull(captured.get().headers().get(RequestCorrelation.REQUEST_ID_HEADER));
    }

    @Test
    void doesNotPropagateAcceptLanguageHeaderWhenRequestHeaderContextContainsLanguage() {
        ExchangeFilterFunction exchangeFilter = filter.filter();
        ClientRequest request = ClientRequest
                .create(HttpMethod.GET, URI.create("https://api.example.test/test"))
                .build();
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        exchangeFilter.filter(request, req -> {
            captured.set(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        })
        .contextWrite(ctx -> ctx.put(
                RequestHeaderContextKeys.CONTEXT_KEY,
                new RequestHeaderContext(null, "req-123", "zh-HK")))
        .block();

        assertNotNull(captured.get());
        assertNull(captured.get().headers().get(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER));
    }

    @Test
    void doesNotPropagateAccountRefHeaderWhenRequestHeaderContextContainsAccountRef() {
        ExchangeFilterFunction exchangeFilter = filter.filter();
        ClientRequest request = ClientRequest
                .create(HttpMethod.GET, URI.create("https://api.example.test/test"))
                .build();
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        exchangeFilter.filter(request, req -> {
            captured.set(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        })
        .contextWrite(ctx -> ctx
                .put(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, "req-123")
                .put(RequestHeaderContextKeys.CONTEXT_KEY,
                        new RequestHeaderContext("ACC-123", "req-123", "en")))
        .block();

        assertNotNull(captured.get());
        assertEquals(List.of("req-123"), captured.get().headers().get(RequestCorrelation.REQUEST_ID_HEADER));
        assertNull(captured.get().headers().get(RequestHeaderContextKeys.ACCOUNT_REF_HEADER));
    }
    @Test
    void propagatesOnlyXRequestIdWhenHeaderContextContainsAccountRefAndAcceptLanguage() {
        ExchangeFilterFunction exchangeFilter = filter.filter();
        ClientRequest request = ClientRequest
                .create(HttpMethod.POST, URI.create("https://api.example.test/ws/NGTPA/v1/TRPGetCountryList"))
                .build();
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        exchangeFilter.filter(request, req -> {
            captured.set(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        })
        .contextWrite(ctx -> ctx
                .put(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, "req-123")
                .put(RequestHeaderContextKeys.CONTEXT_KEY, new RequestHeaderContext("ACC-123", "req-123", "zh-HK")))
        .block();

        assertNotNull(captured.get());
        assertEquals(List.of("req-123"), captured.get().headers().get(RequestCorrelation.REQUEST_ID_HEADER));
        assertNull(captured.get().headers().get(RequestHeaderContextKeys.ACCOUNT_REF_HEADER));
        assertNull(captured.get().headers().get(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER));
    }

}

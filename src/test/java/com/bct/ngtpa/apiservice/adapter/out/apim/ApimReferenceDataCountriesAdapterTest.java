package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetCountryListApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetCountryListDataItem;
import com.bct.ngtpa.apiservice.application.dto.ReferenceDataCountryItem;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.bct.ngtpa.apiservice.shared.web.RequestCorrelation;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContext;
import com.bct.ngtpa.apiservice.shared.web.RequestHeaderContextKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

class ApimReferenceDataCountriesAdapterTest {

    private final ApimReferenceDataCountriesAdapter adapter =
            new ApimReferenceDataCountriesAdapter(null, null, new ApimProperties());

    @Test
    void callsTrpGetCountryListPathWithPostAndEmptyJsonBody() throws Exception {
        CapturingApimWebClientFacade facade = new CapturingApimWebClientFacade();
        FixedEnvelopePayloadCryptoService payloadCrypto = new FixedEnvelopePayloadCryptoService(sampleEnvelope());

        ApimReferenceDataCountriesAdapter flowAdapter =
                new ApimReferenceDataCountriesAdapter(facade, payloadCrypto, new ApimProperties());

        flowAdapter.fetchCountryList().block();

        assertEquals("/ws/NGTPA/v1/TRPGetCountryList", facade.capturedPath);

        String requestJson = new ObjectMapper().writeValueAsString(facade.capturedRequestBody);
        assertEquals("{}", requestJson);

        GetCountryListApimRequest request = (GetCountryListApimRequest) facade.capturedRequestBody;
        assertEquals("{}", new ObjectMapper().writeValueAsString(request));
    }

    @Test
    void mapsApimDataItemsToRawCountryItemsAndPreservesOrder() {
        @SuppressWarnings("unchecked")
        List<ReferenceDataCountryItem> result = (List<ReferenceDataCountryItem>) ReflectionTestUtils.invokeMethod(
                adapter,
                "toCountryItems",
                sampleEnvelope());

        assertEquals(2, result.size());

        assertEquals("HKG", result.get(0).countryCode());
        assertEquals("Hong Kong", result.get(0).countryNameEng());
        assertEquals("香港", result.get(0).countryNameChi());
        assertEquals("852", result.get(0).callingCode());

        assertEquals("CHN", result.get(1).countryCode());
        assertEquals("China", result.get(1).countryNameEng());
        assertEquals("中國", result.get(1).countryNameChi());
        assertEquals("86", result.get(1).callingCode());
    }

    @Test
    void requestPayloadDoesNotContainAccountRefOrAcceptLanguage() throws Exception {
        GetCountryListApimRequest request =
                (GetCountryListApimRequest) ReflectionTestUtils.invokeMethod(adapter, "newRequest");

        String json = new ObjectMapper().writeValueAsString(request);
        assertFalse(json.contains("Account-Ref"));
        assertFalse(json.contains("accountRef"));
        assertFalse(json.contains("Accept-Language"));
        assertFalse(json.contains("acceptLanguage"));
    }

    @Test
    void requestIdFilterPropagatesOnlyXRequestId() {
        ExchangeFilterFunction exchangeFilter = new com.bct.ngtpa.apiservice.adapter.out.apim.client.ApimRequestIdExchangeFilter().filter();
        ClientRequest request = ClientRequest.create(HttpMethod.POST, java.net.URI.create("https://example.test/ws/NGTPA/v1/TRPGetCountryList")).build();
        AtomicReference<ClientRequest> captured = new AtomicReference<>();

        exchangeFilter.filter(request, req -> {
            captured.set(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        }).contextWrite(ctx -> ctx
                .put(RequestCorrelation.REQUEST_ID_CONTEXT_KEY, "req-123")
                .put(RequestHeaderContextKeys.CONTEXT_KEY, new RequestHeaderContext("ACC-123", "req-123", "en")))
                .block();

        assertEquals(List.of("req-123"), captured.get().headers().get(RequestCorrelation.REQUEST_ID_HEADER));
        assertNull(captured.get().headers().get(RequestHeaderContextKeys.ACCOUNT_REF_HEADER));
        assertNull(captured.get().headers().get(RequestHeaderContextKeys.ACCEPT_LANGUAGE_HEADER));
    }

    @Test
    void throwsWhenPayloadMissingOrTopLevelErrMessagePresent() {
        ApimException missingPayload = assertThrows(
                ApimException.class,
                () -> ReflectionTestUtils.invokeMethod(adapter, "toCountryItems", new Object[] {null}));

        ApimResponseEnvelope<GetCountryListDataItem> topLevelErrorEnvelope =
                ApimResponseEnvelope.<GetCountryListDataItem>builder()
                        .response(ApimResponseBody.<GetCountryListDataItem>builder().errMessage("APIM failed").build())
                        .build();

        ApimException topLevelError = assertThrows(
                ApimException.class,
                () -> ReflectionTestUtils.invokeMethod(adapter, "toCountryItems", topLevelErrorEnvelope));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, missingPayload.getErrorCode());
        assertEquals("APIM response payload is invalid: response is missing.", missingPayload.getMessage());

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, topLevelError.getErrorCode());
        assertEquals("APIM failed", topLevelError.getMessage());
    }

    private static ApimResponseEnvelope<GetCountryListDataItem> sampleEnvelope() {
        return ApimResponseEnvelope.<GetCountryListDataItem>builder()
                .response(ApimResponseBody.<GetCountryListDataItem>builder()
                        .errMessage("")
                        .data(List.of(
                                GetCountryListDataItem.builder()
                                        .countryCode("HKG")
                                        .countryNameEng("Hong Kong")
                                        .countryNameChi("香港")
                                        .callingCode("852")
                                        .alphaTwoCode("HK")
                                        .build(),
                                GetCountryListDataItem.builder()
                                        .countryCode("CHN")
                                        .countryNameEng("China")
                                        .countryNameChi("中國")
                                        .callingCode("86")
                                        .alphaTwoCode("CN")
                                        .build()))
                        .build())
                .build();
    }

    private static final class CapturingApimWebClientFacade extends ApimWebClientFacade {
        private String capturedPath;
        private Object capturedRequestBody;

        private CapturingApimWebClientFacade() {
            super(null, new ObjectMapper());
        }

        @Override
        public Mono<String> post(String path, Object requestBody) {
            this.capturedPath = path;
            this.capturedRequestBody = requestBody;
            return Mono.just("ignored");
        }
    }

    private static final class FixedEnvelopePayloadCryptoService extends ApimPayloadCryptoService {
        private final ApimResponseEnvelope<GetCountryListDataItem> envelope;

        private FixedEnvelopePayloadCryptoService(ApimResponseEnvelope<GetCountryListDataItem> envelope) {
            super(new ApimProperties(), new ObjectMapper(), null, null, null, null);
            this.envelope = envelope;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ApimResponseEnvelope<T> decryptResponseEnvelope(
                String apiName,
                String responseJson,
                Class<T> dataClass,
                java.security.PublicKey publicKey) {
            return (ApimResponseEnvelope<T>) envelope;
        }

        @Override
        public <T> T encryptRequest(String apiName, T source, Class<T> targetType, java.security.PublicKey publicKey) {
            return source;
        }
    }
}

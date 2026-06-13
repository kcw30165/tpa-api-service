package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetContributionSummaryApimRequest;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetContributionSummaryContDtlItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetContributionSummaryDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetContributionSummaryDispSrcItem;
import com.bct.ngtpa.apiservice.application.dto.FetchContributionSummaryCommand;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.security.PublicKey;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApimContributionSummaryAdapterTest {

    private final ApimContributionSummaryAdapter adapter = new ApimContributionSummaryAdapter(
            null, null, null, new ApimProperties());

    @Test
    void mapsApimEnvelopeIntoContributionDataset() {
        var response = sampleEnvelope();

        var dataset = (com.bct.ngtpa.apiservice.domain.model.ContributionSummaryDataset) ReflectionTestUtils.invokeMethod(
                adapter, "toContributionSummaryDataset", response);

        assertEquals("HKD", dataset.currency());
        assertEquals(List.of("EE", "ER"), dataset.sources().stream().map(com.bct.ngtpa.apiservice.domain.model.ContributionSource::code).toList());
        assertEquals(2, dataset.entries().size());
        assertEquals(new BigDecimal("17791.75"), dataset.entries().getFirst().amount());
    }

    @Test
    void fetchContributionSummaryUsesDisabledEncryptionFlow() {
        ApimProperties properties = new ApimProperties();
        properties.getEncryption().setEnabled(false);
        FixedDatasetPayloadCryptoService payloadCryptoService = new FixedDatasetPayloadCryptoService(sampleEnvelope());
        FixedBodyApimWebClientFacade facade = new FixedBodyApimWebClientFacade();
        ApimContributionSummaryAdapter flowAdapter = new ApimContributionSummaryAdapter(
                facade,
                new RejectingCertificateService(),
                payloadCryptoService,
                properties);

        var dataset = flowAdapter.fetchContributionSummary(command()).block();

        assertEquals(1, facade.postInvocationCount);
        assertEquals("/ws/NGTPA/v1/TRPGetContSumy", facade.capturedPath);
        assertEquals(2, dataset.entries().size());
        assertNull(payloadCryptoService.lastPublicKey);
    }

    @Test
    void fetchContributionSummaryUsesCertificateWhenEncryptionEnabled() {
        ApimProperties properties = new ApimProperties();
        properties.getEncryption().setEnabled(true);
        FixedDatasetPayloadCryptoService payloadCryptoService = new FixedDatasetPayloadCryptoService(sampleEnvelope());
        FixedBodyApimWebClientFacade facade = new FixedBodyApimWebClientFacade();
        CountingCertificateService certificateService = new CountingCertificateService(new TestPublicKey("bct-public"));
        ApimContributionSummaryAdapter flowAdapter = new ApimContributionSummaryAdapter(
                facade,
                certificateService,
                payloadCryptoService,
                properties);

        var dataset = flowAdapter.fetchContributionSummary(command()).block();

        assertEquals(1, certificateService.invocationCount);
        assertSame(certificateService.publicKey, payloadCryptoService.lastPublicKey);
        assertEquals(2, dataset.entries().size());
    }

    @Test
    void cryptoFailureIsMappedToApimException() {
        ApimProperties properties = new ApimProperties();
        properties.getEncryption().setEnabled(true);
        ApimContributionSummaryAdapter flowAdapter = new ApimContributionSummaryAdapter(
                new FixedBodyApimWebClientFacade(),
                new FailingCertificateService(),
                new FixedDatasetPayloadCryptoService(sampleEnvelope()),
                properties);

        ApimException ex = assertThrows(ApimException.class,
                () -> flowAdapter.fetchContributionSummary(command()).block());

        assertEquals(ErrorCodes.SYSTEM_UNEXPECTED, ex.getErrorCode());
        assertEquals("Certificate crypto error", ex.getMessage());
    }

    @Test
    void toApimRequestDoesNotLeakFrontendOnlyQueryParams() throws Exception {
        FetchContributionSummaryCommand command = command();

        GetContributionSummaryApimRequest request = (GetContributionSummaryApimRequest) ReflectionTestUtils.invokeMethod(
                adapter, "toApimRequest", command);
        String json = new ObjectMapper().writeValueAsString(request);

        assertFalse(json.contains("env"));
        assertFalse(json.contains("mbrType"));
        assertEquals("05/04/2026", request.getCoverFrom());
        assertEquals("05/05/2026", request.getCoverTo());
    }

    @Test
    void throwsWhenPayloadMissingOrTopLevelErrorPresent() {
        ApimException missingPayload = assertThrows(ApimException.class,
                () -> ReflectionTestUtils.invokeMethod(adapter, "toContributionSummaryDataset", new Object[] {null}));
        var response = ApimResponseEnvelope.<GetContributionSummaryDataItem>builder()
                .response(ApimResponseBody.<GetContributionSummaryDataItem>builder().errMessage("APIM failed").build())
                .build();
        ApimException topLevelError = assertThrows(ApimException.class,
                () -> ReflectionTestUtils.invokeMethod(adapter, "toContributionSummaryDataset", response));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, missingPayload.getErrorCode());
        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, topLevelError.getErrorCode());
        assertEquals("APIM response payload is invalid: response is missing.", missingPayload.getMessage());
        assertEquals("APIM failed", topLevelError.getMessage());
    }

    private static FetchContributionSummaryCommand command() {
        return new FetchContributionSummaryCommand("JP", "MBR", "05/04/2026", "05/05/2026", "policy-no", "2", "user-id", "", "");
    }

    private static ApimResponseEnvelope<GetContributionSummaryDataItem> sampleEnvelope() {
        return ApimResponseEnvelope.<GetContributionSummaryDataItem>builder()
                .response(ApimResponseBody.<GetContributionSummaryDataItem>builder()
                        .errMessage("")
                        .data(List.of(GetContributionSummaryDataItem.builder()
                                .currency("HKD")
                                .dispSrc(List.of(
                                        GetContributionSummaryDispSrcItem.builder().dispSrc("EE").srcDesc("Member").srcChinDesc("").seq(20).build(),
                                        GetContributionSummaryDispSrcItem.builder().dispSrc("ER").srcDesc("Company").srcChinDesc("").seq(10).build()))
                                .contDtl(List.of(
                                        GetContributionSummaryContDtlItem.builder().dispSrc("ER").coverFrom("01/03/2026").coverTo("31/03/2026").dealDate("01/03/2026").contAmt(new BigDecimal("17791.75")).build(),
                                        GetContributionSummaryContDtlItem.builder().dispSrc("EE").coverFrom("01/03/2026").coverTo("31/03/2026").dealDate("01/03/2026").contAmt(new BigDecimal("7116.7")).build()))
                                .build()))
                        .build())
                .build();
    }

    private static final class FixedBodyApimWebClientFacade extends ApimWebClientFacade {
        private String capturedPath;
        private int postInvocationCount;

        private FixedBodyApimWebClientFacade() {
            super(null, new ObjectMapper());
        }

        @Override
        public Mono<String> post(String path, Object requestBody) {
            this.capturedPath = path;
            this.postInvocationCount++;
            return Mono.just("ignored");
        }
    }

    private static final class FixedDatasetPayloadCryptoService extends ApimPayloadCryptoService {
        private final ApimResponseEnvelope<GetContributionSummaryDataItem> envelope;
        private PublicKey lastPublicKey;

        private FixedDatasetPayloadCryptoService(ApimResponseEnvelope<GetContributionSummaryDataItem> envelope) {
            super(new ApimProperties(), new ObjectMapper(), null, null, null, null);
            this.envelope = envelope;
        }

        @Override
        public <T> T encryptRequest(String apiName, T source, Class<T> targetType, PublicKey publicKey) {
            this.lastPublicKey = publicKey;
            return source;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ApimResponseEnvelope<T> decryptResponseEnvelope(String apiName, String responseJson, Class<T> dataClass,
                PublicKey publicKey) {
            this.lastPublicKey = publicKey;
            return (ApimResponseEnvelope<T>) envelope;
        }
    }

    private static final class CountingCertificateService extends ApimCertificateService {
        private final PublicKey publicKey;
        private int invocationCount;

        private CountingCertificateService(PublicKey publicKey) {
            super(null, new ApimProperties(), null);
            this.publicKey = publicKey;
        }

        @Override
        public Mono<PublicKey> getBctPublicKey() {
            invocationCount++;
            return Mono.just(publicKey);
        }
    }

    private static final class RejectingCertificateService extends ApimCertificateService {
        private RejectingCertificateService() {
            super(null, new ApimProperties(), null);
        }

        @Override
        public Mono<PublicKey> getBctPublicKey() {
            return Mono.error(new AssertionError("Certificate lookup should not run when encryption is disabled."));
        }
    }

    private static final class FailingCertificateService extends ApimCertificateService {
        private FailingCertificateService() {
            super(null, new ApimProperties(), null);
        }

        @Override
        public Mono<PublicKey> getBctPublicKey() {
            return Mono.error(new ApimCryptoException("Certificate crypto error"));
        }
    }

    private record TestPublicKey(String value) implements PublicKey {
        @Override
        public String getAlgorithm() {
            return "RSA";
        }

        @Override
        public String getFormat() {
            return "X.509";
        }

        @Override
        public byte[] getEncoded() {
            return value.getBytes();
        }
    }
}
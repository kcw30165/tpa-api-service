package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimPayloadFieldTransformer;
import com.bct.ngtpa.apiservice.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimRsaPayloadCrypto;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimAesKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.apim.ApimAppCertificateService;
import org.junit.jupiter.api.Test;
import org.springframework.util.CollectionUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApimPayloadCryptoServiceFieldSelectionTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final class RecordingTransformer extends ApimPayloadFieldTransformer {
        Set<String> lastTargetFields;

        @Override
        public JsonNode transformFields(JsonNode sourceNode, Set<String> targetFields, UnaryOperator<String> transformFn) {
            this.lastTargetFields = new LinkedHashSet<>(targetFields);
            return sourceNode;
        }
    }

    @Test
    void usesGlobalRequestFieldsForDifferentApis() {
        ApimProperties props = new ApimProperties();
        props.getEncryption().setEnabled(true);
        props.getEncryption().setRequestFields(List.of("policy-no", "cert-no"));

        RecordingTransformer transformer = new RecordingTransformer();
        ApimPayloadCryptoService svc = new ApimPayloadCryptoService(props, OBJECT_MAPPER, transformer,
            (ApimRsaPayloadCrypto) null, (ApimAesKeyFactory) null, (ApimAppCertificateService) null);

        Map<String, Object> request = Map.of("policy-no", "123", "other", "x");
        svc.encryptRequest("/ws/NGTPA/v1/TRPGetMsgBoard", request, Map.class);

        assertEquals(new LinkedHashSet<>(List.of("policy-no", "cert-no")), transformer.lastTargetFields);

        transformer.lastTargetFields = null;
        svc.encryptRequest("/ws/NGTPA/v1/TRPUpdMsgRead", request, Map.class);
        assertEquals(new LinkedHashSet<>(List.of("policy-no", "cert-no")), transformer.lastTargetFields);
    }

    @Test
    void doesNotInvokeTransformerWhenEncryptionDisabled() {
        ApimProperties props = new ApimProperties();
        props.getEncryption().setEnabled(false);

        RecordingTransformer transformer = new RecordingTransformer();
        ApimPayloadCryptoService svc = new ApimPayloadCryptoService(props, OBJECT_MAPPER, transformer,
            (ApimRsaPayloadCrypto) null, (ApimAesKeyFactory) null, (ApimAppCertificateService) null);

        Map<String, Object> request = Map.of("policy-no", "123");
        Object out = svc.encryptRequest("/ws/NGTPA/v1/TRPGetMsgBoard", request, Map.class);

        assertEquals(request, out);
        assertNull(transformer.lastTargetFields);
    }
}

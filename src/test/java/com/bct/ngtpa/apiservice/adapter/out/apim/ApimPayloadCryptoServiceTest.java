package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimAesKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimPayloadFieldTransformer;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimRsaPayloadCrypto;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ApimPayloadCryptoServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static KeyPair keyPair;

    @BeforeAll
    static void createKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        keyPair = keyPairGenerator.generateKeyPair();
    }

    @Test
    void encryptsConfiguredFieldsAndDecryptsThemBack() throws Exception {
        ApimPayloadCryptoService service = service(encryptionProperties(List.of("secret"), List.of("secret")));
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("secret", "root-value");
        request.put("plain", "keep");
        request.put("nested", Map.of("secret", "nested-value", "count", 2));

        Map<String, Object> encrypted = service.encryptRequest("/ws/NGTPA/v1/TRPGetMsgBoard", request, Map.class,
                keyPair.getPublic());
        Map<String, Object> decrypted = service.decryptResponse("/ws/NGTPA/v1/TRPGetMsgBoard",
                OBJECT_MAPPER.writeValueAsString(encrypted), Map.class, keyPair.getPublic());

        assertNotEquals("root-value", encrypted.get("secret"));
        assertEquals("keep", encrypted.get("plain"));
        assertEquals(request, decrypted);
    }

    @Test
    void decryptsEncryptedResponseEnvelope() throws Exception {
        ApimPayloadCryptoService service = service(encryptionProperties(List.of("secret"), List.of("secret")));
        Map<String, Object> encryptedItem = service.encryptRequest("/ws/NGTPA/v1/TRPGetMsgBoard",
                Map.of("secret", "payload", "plain", "visible"), Map.class, keyPair.getPublic());
        String responseJson = OBJECT_MAPPER.writeValueAsString(Map.of(
                "response", Map.of(
                        "err-message", "",
                        "data", List.of(encryptedItem))));

        ApimResponseEnvelope<Map> envelope = service.decryptResponseEnvelope("/ws/NGTPA/v1/TRPGetMsgBoard",
                responseJson, Map.class, keyPair.getPublic());

        assertEquals("payload", envelope.getResponse().getData().getFirst().get("secret"));
        assertEquals("visible", envelope.getResponse().getData().getFirst().get("plain"));
    }

    @Test
    void returnsInputOrParsedJsonWhenEncryptionOrFieldsDoNotApply() {
        ApimPayloadCryptoService disabledService = service(disabledProperties());
        Map<String, Object> request = Map.of("secret", "value");
        String responseJson = "{\"secret\":\"value\"}";

        assertSame(request, disabledService.encryptRequest("/ws/NGTPA/v1/TRPGetMsgBoard", request, Map.class));
        assertEquals(request, disabledService.decryptResponse("/ws/NGTPA/v1/TRPGetMsgBoard", responseJson, Map.class));

        ApimPayloadCryptoService noFieldService = service(encryptionProperties(List.of(), List.of()));
        assertSame(request, noFieldService.encryptRequest("/ws/NGTPA/v1/TRPGetMsgBoard", request, Map.class));
        assertEquals(request, noFieldService.decryptResponse("/ws/NGTPA/v1/TRPGetMsgBoard", responseJson, Map.class));
    }

    @Test
    void preservesBlankFieldValues() throws Exception {
        ApimPayloadCryptoService service = service(encryptionProperties(List.of("secret"), List.of("secret")));
        Map<String, Object> request = Map.of("secret", " ", "plain", "keep");

        Map<String, Object> encrypted = service.encryptRequest("/ws/NGTPA/v1/TRPGetMsgBoard", request, Map.class,
                keyPair.getPublic());
        Map<String, Object> decrypted = service.decryptResponse("/ws/NGTPA/v1/TRPGetMsgBoard",
                OBJECT_MAPPER.writeValueAsString(encrypted), Map.class, keyPair.getPublic());

        assertEquals(" ", encrypted.get("secret"));
        assertEquals(request, decrypted);
    }

    @Test
    void wrapsParseErrorsWhenJsonCannotBeRead() {
        ApimPayloadCryptoService service = service(disabledProperties());

        ApimCryptoException error = assertThrows(ApimCryptoException.class,
                () -> service.decryptResponse("/ws/NGTPA/v1/TRPGetMsgBoard", "not-json", Map.class));

        assertEquals("Failed to parse APIM response payload.", error.getMessage());
    }

    @Test
    void wrapsInvalidEncryptedFieldFormats() {
        ApimPayloadCryptoService service = service(encryptionProperties(List.of("secret"), List.of("secret")));
        String responseJson = "{\"secret\":\"missing-separator\"}";

        ApimCryptoException error = assertThrows(ApimCryptoException.class,
                () -> service.decryptResponse("/ws/NGTPA/v1/TRPGetMsgBoard", responseJson, Map.class, keyPair.getPublic()));

        assertEquals("Failed to decrypt APIM response payload.", error.getMessage());
        assertEquals("Invalid APIM encrypted field format.", error.getCause().getMessage());
    }

    private static ApimPayloadCryptoService service(ApimProperties properties) {
        ApimAppCertificateService appCertificateService = mock(ApimAppCertificateService.class);
        when(appCertificateService.getAppPrivateKey()).thenReturn(keyPair.getPrivate());
        return new ApimPayloadCryptoService(
                properties,
                OBJECT_MAPPER,
                new ApimPayloadFieldTransformer(),
                new ApimRsaPayloadCrypto(OBJECT_MAPPER),
                new ApimAesKeyFactory(),
                appCertificateService);
    }

    private static ApimProperties disabledProperties() {
        ApimProperties properties = new ApimProperties();
        properties.getEncryption().setEnabled(false);
        return properties;
    }

    private static ApimProperties encryptionProperties(List<String> requestFields, List<String> responseFields) {
        ApimProperties properties = new ApimProperties();
        properties.getEncryption().setEnabled(true);
        properties.getEncryption().setRequestFields(requestFields);
        properties.getEncryption().setResponseFields(responseFields);
        return properties;
    }
}
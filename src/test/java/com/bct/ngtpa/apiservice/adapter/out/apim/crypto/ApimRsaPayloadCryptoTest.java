package com.bct.ngtpa.apiservice.adapter.out.apim.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ApimRsaPayloadCryptoTest {

    private static KeyPair keyPair;

    private final ApimRsaPayloadCrypto crypto = new ApimRsaPayloadCrypto(new ObjectMapper());

    @BeforeAll
    static void createKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        keyPair = keyPairGenerator.generateKeyPair();
    }

    @Test
    void encryptsPlaintextAndDecryptsItToHex() {
        String cipherText = crypto.encryptPlaintext("Abc123XYZ", keyPair.getPublic());
        String decryptedHex = crypto.decryptRsa(cipherText, keyPair.getPrivate());

        assertEquals(toHex("Abc123XYZ".getBytes(StandardCharsets.UTF_8)), decryptedHex);
    }

    @Test
    void signsAndVerifiesJwtPayload() {
        String jwt = crypto.createSignedJwt("payload-value", keyPair.getPrivate());

        assertEquals("payload-value", crypto.verifyAndExtractValue(jwt, keyPair.getPublic()));
    }

    @Test
    void returnsBlankInputsUnchangedForJwtOperations() {
        assertEquals("", crypto.createSignedJwt("", keyPair.getPrivate()));
        assertEquals("  ", crypto.verifyAndExtractValue("  ", keyPair.getPublic()));
    }

    @Test
    void rejectsMissingPlaintextOrCiphertext() {
        IllegalArgumentException encryptError = assertThrows(IllegalArgumentException.class,
                () -> crypto.encryptPlaintext(" ", keyPair.getPublic()));
        IllegalArgumentException decryptError = assertThrows(IllegalArgumentException.class,
                () -> crypto.decryptRsa("", keyPair.getPrivate()));

        assertEquals("RSA plaintext is required.", encryptError.getMessage());
        assertEquals("RSA ciphertext is required.", decryptError.getMessage());
    }

    @Test
    void rejectsMissingKeys() {
        ApimCryptoException publicKeyError = assertThrows(ApimCryptoException.class,
                () -> crypto.encryptPlaintext("value", null));
        ApimCryptoException privateKeyError = assertThrows(ApimCryptoException.class,
                () -> crypto.decryptRsa("cipher-text", null));
        ApimCryptoException signingKeyError = assertThrows(ApimCryptoException.class,
                () -> crypto.createSignedJwt("value", null));
        ApimCryptoException verifyKeyError = assertThrows(ApimCryptoException.class,
                () -> crypto.verifyAndExtractValue("header.payload.signature", null));

        assertEquals("APIM RSA public key is required.", publicKeyError.getMessage());
        assertEquals("APIM RSA private key is required.", privateKeyError.getMessage());
        assertEquals("APIM signing private key is required.", signingKeyError.getMessage());
        assertEquals("APIM verification public key is required.", verifyKeyError.getMessage());
    }

    @Test
    void rejectsInvalidJwtFormatsAndSignatures() {
        ApimCryptoException invalidFormat = assertThrows(ApimCryptoException.class,
                () -> crypto.verifyAndExtractValue("only.two", keyPair.getPublic()));

        String signedJwt = crypto.createSignedJwt("payload-value", keyPair.getPrivate());
        String[] jwtParts = signedJwt.split("\\.");
        String tamperedPayload = jwtParts[1].substring(0, jwtParts[1].length() - 1)
            + (jwtParts[1].endsWith("A") ? "B" : "A");
        String tamperedJwt = jwtParts[0] + "." + tamperedPayload + "." + jwtParts[2];
        ApimCryptoException invalidSignature = assertThrows(ApimCryptoException.class,
                () -> crypto.verifyAndExtractValue(tamperedJwt, keyPair.getPublic()));

        assertEquals("Invalid APIM JWT format.", invalidFormat.getMessage());
        assertTrue(invalidSignature.getMessage().contains("verification")
                || invalidSignature.getMessage().contains("Unable to verify"));
    }

    @Test
    void rejectsJwtPayloadWithoutValue() throws Exception {
        String jwt = signedJwtForPayload("{\"other\":\"value\"}");

        ApimCryptoException error = assertThrows(ApimCryptoException.class,
                () -> crypto.verifyAndExtractValue(jwt, keyPair.getPublic()));

        assertEquals("APIM JWT payload does not contain value.", error.getMessage());
    }

    private String signedJwtForPayload(String payloadJson) throws Exception {
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;

        java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        String encodedSignature = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        return signingInput + "." + encodedSignature;
    }

    private static String toHex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (byte currentByte : value) {
            builder.append(String.format("%02x", currentByte));
        }
        return builder.toString();
    }
}
package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimAesKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimCryptoException;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimPayloadFieldTransformer;
import com.bct.ngtpa.apiservice.adapter.out.apim.crypto.ApimRsaPayloadCrypto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pins the APIM payload field-encryption wire contract implemented by {@link
 * ApimPayloadCryptoService}: {@code <RSA-OAEP(AES key)>:<Base64(IV || AES/CBC/PKCS5Padding(RS256
 * JWT))>}. APIM will not change this cipher, so SonarQube java:S5542 is accepted rather than fixed
 * (docs/integration/apim/apim-payload-aes-cbc-sonar-s5542-decision.md). Every cryptographic step in
 * this test is an independent {@code javax.crypto} implementation using literal algorithm names, so
 * changing the production transformation fails these tests.
 */
class ApimPayloadCryptoServiceWireContractTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String API_NAME = "/ws/NGTPA/v1/TRPGetMsgBoard";
  private static final String AES_TRANSFORMATION = "AES/CBC/PKCS5Padding";
  private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
  private static final String JWT_HEADER_JSON = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
  private static final int AES_IV_LENGTH_BYTES = 16;
  private static final int AES_KEY_LENGTH_BYTES = 32;
  private static final String INDEPENDENT_AES_KEY = "Abcdefghij0123456789ABCDEFGHIJ01";

  private static KeyPair keyPair;

  @BeforeAll
  static void createKeyPair() throws Exception {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    keyPair = keyPairGenerator.generateKeyPair();
  }

  @Test
  void fieldIsDecryptableWithIndependentAesCbcPkcs5Implementation() throws Exception {
    ApimPayloadCryptoService service =
        service(new ApimAesKeyFactory(), List.of("secret"), List.of("secret"));
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("secret", "wire-contract-value");
    request.put("plain", "keep");

    Map<String, Object> encrypted =
        service.encryptRequest(API_NAME, request, Map.class, keyPair.getPublic());

    assertEquals("keep", encrypted.get("plain"));
    String[] parts = ((String) encrypted.get("secret")).split(":", 2);
    assertEquals(2, parts.length);

    byte[] aesKeyBytes = rsaDecrypt(parts[0]);
    assertEquals(AES_KEY_LENGTH_BYTES, aesKeyBytes.length);
    assertTrue(new String(aesKeyBytes, StandardCharsets.UTF_8).matches("[A-Za-z0-9]{32}"));

    byte[] combined = Base64.getDecoder().decode(parts[1]);
    assertTrue(combined.length > AES_IV_LENGTH_BYTES);
    assertEquals(0, (combined.length - AES_IV_LENGTH_BYTES) % 16);
    byte[] iv = Arrays.copyOfRange(combined, 0, AES_IV_LENGTH_BYTES);
    byte[] cipherText = Arrays.copyOfRange(combined, AES_IV_LENGTH_BYTES, combined.length);

    Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
    cipher.init(
        Cipher.DECRYPT_MODE, new SecretKeySpec(aesKeyBytes, "AES"), new IvParameterSpec(iv));
    String jwt = new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);

    String[] jwtParts = jwt.split("\\.");
    assertEquals(3, jwtParts.length);
    assertEquals(
        JWT_HEADER_JSON,
        new String(Base64.getUrlDecoder().decode(jwtParts[0]), StandardCharsets.UTF_8));
    JsonNode payload = OBJECT_MAPPER.readTree(Base64.getUrlDecoder().decode(jwtParts[1]));
    assertEquals("wire-contract-value", payload.get("value").asText());

    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initVerify(keyPair.getPublic());
    signature.update((jwtParts[0] + "." + jwtParts[1]).getBytes(StandardCharsets.UTF_8));
    assertTrue(signature.verify(Base64.getUrlDecoder().decode(jwtParts[2])));
  }

  @Test
  void fieldBuiltWithIndependentAesCbcPkcs5ImplementationIsDecrypted() throws Exception {
    ApimPayloadCryptoService service =
        service(new ApimAesKeyFactory(), List.of("secret"), List.of("secret"));
    byte[] aesKeyBytes = INDEPENDENT_AES_KEY.getBytes(StandardCharsets.UTF_8);
    assertEquals(AES_KEY_LENGTH_BYTES, aesKeyBytes.length);
    byte[] iv = new byte[AES_IV_LENGTH_BYTES];
    for (int i = 0; i < iv.length; i++) {
      iv[i] = (byte) i;
    }

    Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
    cipher.init(
        Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKeyBytes, "AES"), new IvParameterSpec(iv));
    byte[] cipherText =
        cipher.doFinal(signedJwt("independent-value").getBytes(StandardCharsets.UTF_8));
    byte[] combined = new byte[iv.length + cipherText.length];
    System.arraycopy(iv, 0, combined, 0, iv.length);
    System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
    String wireField =
        Base64.getEncoder().encodeToString(rsaEncrypt(aesKeyBytes))
            + ":"
            + Base64.getEncoder().encodeToString(combined);
    String responseJson =
        OBJECT_MAPPER.writeValueAsString(Map.of("secret", wireField, "plain", "keep"));

    Map<String, Object> decrypted =
        service.decryptResponse(API_NAME, responseJson, Map.class, keyPair.getPublic());

    assertEquals("independent-value", decrypted.get("secret"));
    assertEquals("keep", decrypted.get("plain"));
  }

  @Test
  void invalidAesKeyLengthSurfacesAsNestedApimCryptoException() {
    ApimAesKeyFactory shortKeyFactory =
        new ApimAesKeyFactory() {
          @Override
          public SecretKey generateKey() {
            return new SecretKeySpec(new byte[10], "AES");
          }
        };
    ApimPayloadCryptoService service =
        service(shortKeyFactory, List.of("secret"), List.of("secret"));
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("secret", "wire-contract-value");

    ApimCryptoException error =
        assertThrows(
            ApimCryptoException.class,
            () -> service.encryptRequest(API_NAME, request, Map.class, keyPair.getPublic()));

    assertEquals("Failed to transform APIM payload.", error.getMessage());
    assertInstanceOf(ApimCryptoException.class, error.getCause());
    assertEquals("Failed to encrypt APIM field payload.", error.getCause().getMessage());
  }

  @Test
  void ciphertextWithInvalidBlockLengthSurfacesAsNestedApimCryptoException() throws Exception {
    ApimPayloadCryptoService service =
        service(new ApimAesKeyFactory(), List.of("secret"), List.of("secret"));
    byte[] aesKeyBytes = INDEPENDENT_AES_KEY.getBytes(StandardCharsets.UTF_8);
    byte[] combined = new byte[AES_IV_LENGTH_BYTES + 5];
    String wireField =
        Base64.getEncoder().encodeToString(rsaEncrypt(aesKeyBytes))
            + ":"
            + Base64.getEncoder().encodeToString(combined);
    String responseJson = OBJECT_MAPPER.writeValueAsString(Map.of("secret", wireField));

    ApimCryptoException error =
        assertThrows(
            ApimCryptoException.class,
            () -> service.decryptResponse(API_NAME, responseJson, Map.class, keyPair.getPublic()));

    assertEquals("Failed to decrypt APIM response payload.", error.getMessage());
    assertInstanceOf(ApimCryptoException.class, error.getCause());
    assertEquals("Failed to decrypt APIM field payload.", error.getCause().getMessage());
  }

  private static ApimPayloadCryptoService service(
      ApimAesKeyFactory aesKeyFactory, List<String> requestFields, List<String> responseFields) {
    ApimProperties properties = new ApimProperties();
    properties.getEncryption().setEnabled(true);
    properties.getEncryption().setRequestFields(requestFields);
    properties.getEncryption().setResponseFields(responseFields);
    ApimAppCertificateService appCertificateService = mock(ApimAppCertificateService.class);
    when(appCertificateService.getAppPrivateKey()).thenReturn(keyPair.getPrivate());
    return new ApimPayloadCryptoService(
        properties,
        OBJECT_MAPPER,
        new ApimPayloadFieldTransformer(),
        new ApimRsaPayloadCrypto(OBJECT_MAPPER),
        aesKeyFactory,
        appCertificateService);
  }

  private static OAEPParameterSpec oaepParameters() {
    return new OAEPParameterSpec(
        "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
  }

  private static byte[] rsaEncrypt(byte[] plain) throws Exception {
    Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
    cipher.init(Cipher.ENCRYPT_MODE, keyPair.getPublic(), oaepParameters());
    return cipher.doFinal(plain);
  }

  private static byte[] rsaDecrypt(String base64CipherText) throws Exception {
    Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
    cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate(), oaepParameters());
    return cipher.doFinal(Base64.getDecoder().decode(base64CipherText));
  }

  private static String signedJwt(String value) throws Exception {
    String header =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(JWT_HEADER_JSON.getBytes(StandardCharsets.UTF_8));
    String payload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(OBJECT_MAPPER.writeValueAsBytes(Map.of("value", value)));
    String signingInput = header + "." + payload;
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(keyPair.getPrivate());
    signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
    return signingInput
        + "."
        + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
  }
}

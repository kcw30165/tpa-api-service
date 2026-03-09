package com.bct.ngtpa.apiservice.service;

import com.bct.ngtpa.apiservice.config.ApimProperties;
import com.bct.ngtpa.apiservice.config.ApimProperties.ApiFieldEncryptionConfig;
import com.bct.ngtpa.apiservice.util.apim.JsonFieldCryptoUtil;
import com.bct.ngtpa.apiservice.util.apim.RsaFieldCryptoUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.UnaryOperator;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
public class ApimPayloadCryptoService {
    private static final String AES_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String AES_ALGORITHM = "AES";
    private static final int AES_KEY_LENGTH_BYTES = 32;
    private static final int AES_IV_LENGTH_BYTES = 16;

    private final ApimProperties apimProperties;
    private final ObjectMapper objectMapper;
    private final JsonFieldCryptoUtil jsonFieldCryptoUtil;
    private final RsaFieldCryptoUtil rsaFieldCryptoUtil;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApimPayloadCryptoService(
            ApimProperties apimProperties,
            ObjectMapper objectMapper,
            JsonFieldCryptoUtil jsonFieldCryptoUtil,
            RsaFieldCryptoUtil rsaFieldCryptoUtil) {
        this.apimProperties = apimProperties;
        this.objectMapper = objectMapper;
        this.jsonFieldCryptoUtil = jsonFieldCryptoUtil;
        this.rsaFieldCryptoUtil = rsaFieldCryptoUtil;
    }

    public <T> T encryptRequest(String apiName, T source, Class<T> targetType) {
        if (!apimProperties.getEncryption().isEnabled()) {
            return source;
        }
        Set<String> targetFields = getRequestFields(apiName);
        if (CollectionUtils.isEmpty(targetFields)) {
            return source;
        }
        return transformObject(source, targetType, targetFields, this::encryptField);
    }

    public <T> T decryptResponse(String apiName, String responseJson, Class<T> targetType) {
        if (!apimProperties.getEncryption().isEnabled()) {
            return parseJson(responseJson, targetType);
        }
        Set<String> targetFields = getResponseFields(apiName);
        if (CollectionUtils.isEmpty(targetFields)) {
            return parseJson(responseJson, targetType);
        }

        try {
            JsonNode sourceNode = objectMapper.readTree(responseJson);
            JsonNode transformedNode = jsonFieldCryptoUtil.transformFields(sourceNode, targetFields, this::decryptField);
            return objectMapper.treeToValue(transformedNode, targetType);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt APIM response payload.", ex);
        }
    }

    private <T> T transformObject(
            T source,
            Class<T> targetType,
            Set<String> targetFields,
            UnaryOperator<String> transformFn) {
        try {
            JsonNode sourceNode = objectMapper.valueToTree(source);
            JsonNode transformedNode = jsonFieldCryptoUtil.transformFields(sourceNode, targetFields, transformFn);
            return objectMapper.treeToValue(transformedNode, targetType);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to transform APIM payload.", ex);
        }
    }

    private String encryptField(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return plainText;
        }

        try {
            byte[] aesKey = resolveRequestAesKey();
            String signedJwt = rsaFieldCryptoUtil.createSignedJwt(plainText);
            String encryptedAesKey = rsaFieldCryptoUtil.encryptKey(aesKey);
            String encryptedPayload = encryptAes(signedJwt, aesKey);
            return encryptedAesKey + ":" + encryptedPayload;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt APIM field payload.", ex);
        }
    }

    private String decryptField(String encryptedFieldValue) {
        if (!StringUtils.hasText(encryptedFieldValue)) {
            return encryptedFieldValue;
        }

        String[] parts = encryptedFieldValue.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalStateException("Invalid APIM encrypted field format.");
        }

        try {
            byte[] aesKey = rsaFieldCryptoUtil.decryptKey(parts[0]);
            String signedJwt = decryptAes(parts[1], aesKey);
            return rsaFieldCryptoUtil.verifyAndExtractValue(signedJwt);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt APIM field payload.", ex);
        }
    }

    private String encryptAes(String plainText, byte[] aesKey) throws Exception {
        byte[] iv = randomBytes(AES_IV_LENGTH_BYTES);
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(
                Cipher.ENCRYPT_MODE,
                new SecretKeySpec(aesKey, AES_ALGORITHM),
                new IvParameterSpec(iv));

        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        byte[] ivAndCipherText = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, ivAndCipherText, 0, iv.length);
        System.arraycopy(cipherText, 0, ivAndCipherText, iv.length, cipherText.length);
        return java.util.Base64.getEncoder().encodeToString(ivAndCipherText);
    }

    private String decryptAes(String encryptedValue, byte[] aesKey) throws Exception {
        byte[] ivAndCipherText = java.util.Base64.getDecoder().decode(encryptedValue);
        if (ivAndCipherText.length <= AES_IV_LENGTH_BYTES) {
            throw new IllegalStateException("Invalid APIM AES payload.");
        }

        byte[] iv = new byte[AES_IV_LENGTH_BYTES];
        byte[] cipherText = new byte[ivAndCipherText.length - AES_IV_LENGTH_BYTES];
        System.arraycopy(ivAndCipherText, 0, iv, 0, AES_IV_LENGTH_BYTES);
        System.arraycopy(ivAndCipherText, AES_IV_LENGTH_BYTES, cipherText, 0, cipherText.length);

        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(aesKey, AES_ALGORITHM),
                new IvParameterSpec(iv));
        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }

    private byte[] resolveRequestAesKey() {
        String configuredAesKey = apimProperties.getEncryption().getAesKey();
        if (!StringUtils.hasText(configuredAesKey)) {
            return randomBytes(AES_KEY_LENGTH_BYTES);
        }

        byte[] aesKey = configuredAesKey.getBytes(StandardCharsets.UTF_8);
        if (aesKey.length != 16 && aesKey.length != 24 && aesKey.length != 32) {
            throw new IllegalStateException(
                    "apim.encryption.aesKey must be 16, 24, or 32 bytes when encoded as UTF-8.");
        }
        return aesKey;
    }

    private byte[] randomBytes(int length) {
        byte[] randomValue = new byte[length];
        secureRandom.nextBytes(randomValue);
        return randomValue;
    }

    private <T> T parseJson(String sourceJson, Class<T> targetType) {
        try {
            return objectMapper.readValue(sourceJson, targetType);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse APIM response payload.", ex);
        }
    }

    private Set<String> getRequestFields(String apiName) {
        ApiFieldEncryptionConfig config = apimProperties.getEncryption().getApis().get(apiName);
        if (config == null || CollectionUtils.isEmpty(config.getRequestFields())) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(config.getRequestFields());
    }

    private Set<String> getResponseFields(String apiName) {
        ApiFieldEncryptionConfig config = apimProperties.getEncryption().getApis().get(apiName);
        if (config == null || CollectionUtils.isEmpty(config.getResponseFields())) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(config.getResponseFields());
    }
}

package com.bct.ngtpa.apiservice.adapter.out.apim;

import com.bct.ngtpa.apiservice.config.ApimProperties;
import com.bct.ngtpa.apiservice.config.ApimProperties.ApiFieldEncryptionConfig;
import com.bct.ngtpa.apiservice.util.apim.ApimCertUtility;
import com.bct.ngtpa.apiservice.util.apim.JsonFieldCryptoUtil;
import com.bct.ngtpa.apiservice.util.apim.RsaFieldCryptoUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.UnaryOperator;

@Service
@RequiredArgsConstructor
public class ApimPayloadCryptoService {

    private static final String AES_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String AES_ALGORITHM = "AES";
    private static final int AES_IV_LENGTH_BYTES = 16;

    private final ApimProperties apimProperties;
    private final ObjectMapper objectMapper;
    private final JsonFieldCryptoUtil jsonFieldCryptoUtil;
    private final RsaFieldCryptoUtil rsaFieldCryptoUtil;
    private final ApimAppCertificateService apimAppCertificateService;

    public <T> T encryptRequest(String apiName, T source, Class<T> targetType) {
        return encryptRequest(apiName, source, targetType, null);
    }

    public <T> T encryptRequest(String apiName, T source, Class<T> targetType, PublicKey publicKey) {
        if (!apimProperties.getEncryption().isEnabled()) {
            return source;
        }
        Set<String> targetFields = getRequestFields(apiName);
        if (CollectionUtils.isEmpty(targetFields)) {
            return source;
        }
        return transformObject(source, targetType, targetFields, fieldValue -> encryptField(fieldValue, publicKey));
    }

    public <T> T decryptResponse(String apiName, String responseJson, Class<T> targetType) {
        return decryptResponse(apiName, responseJson, targetType, null);
    }

    public <T> T decryptResponse(String apiName, String responseJson, Class<T> targetType, PublicKey publicKey) {
        if (!apimProperties.getEncryption().isEnabled()) {
            return parseJson(responseJson, targetType);
        }
        Set<String> targetFields = getResponseFields(apiName);
        if (CollectionUtils.isEmpty(targetFields)) {
            return parseJson(responseJson, targetType);
        }

        try {
            JsonNode sourceNode = objectMapper.readTree(responseJson);
            JsonNode transformedNode = jsonFieldCryptoUtil.transformFields(
                    sourceNode, targetFields, fieldValue -> decryptField(fieldValue, publicKey));
            return objectMapper.treeToValue(transformedNode, targetType);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt APIM response payload.", ex);
        }
    }

    private <T> T transformObject(T source, Class<T> targetType, Set<String> targetFields,
            UnaryOperator<String> transformFn) {
        try {
            JsonNode sourceNode = objectMapper.valueToTree(source);
            JsonNode transformedNode = jsonFieldCryptoUtil.transformFields(sourceNode, targetFields, transformFn);
            return objectMapper.treeToValue(transformedNode, targetType);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to transform APIM payload.", ex);
        }
    }

    private String encryptField(String plainText, PublicKey publicKey) {
        if (!StringUtils.hasText(plainText)) {
            return plainText;
        }
        try {
            SecretKey aesKey = ApimCertUtility.getAesKey();
            String signedJwt = rsaFieldCryptoUtil.createSignedJwt(plainText, apimAppCertificateService.getAppPrivateKey());
            String encryptedPayload = encryptAes(signedJwt, aesKey);
            String encryptedAesKey = rsaFieldCryptoUtil.publicKeyEncryptPlaintext(
                    new String(aesKey.getEncoded(), StandardCharsets.UTF_8), publicKey);
            return encryptedAesKey + ":" + encryptedPayload;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt APIM field payload.", ex);
        }
    }

    private String decryptField(String encryptedFieldValue, PublicKey publicKey) {
        if (!StringUtils.hasText(encryptedFieldValue)) {
            return encryptedFieldValue;
        }
        String[] parts = encryptedFieldValue.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalStateException("Invalid APIM encrypted field format.");
        }
        try {
            String aesKeyHex = rsaFieldCryptoUtil.decryptRsa(parts[0], apimAppCertificateService.getAppPrivateKey());
            String signedJwt = decryptAes(parts[1], aesKeyHex);
            return rsaFieldCryptoUtil.verifyAndExtractValue(signedJwt, publicKey);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt APIM field payload.", ex);
        }
    }

    private String encryptAes(String plainText, SecretKey aesKey) throws Exception {
        byte[] iv = new byte[AES_IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new IvParameterSpec(iv));
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        return Base64.getEncoder().encodeToString(combined);
    }

    private String decryptAes(String encryptedValue, String aesKeyHex) throws Exception {
        byte[] aesKeyBytes = hexStringToByteArray(aesKeyHex);
        SecretKeySpec keySpec = new SecretKeySpec(aesKeyBytes, AES_ALGORITHM);
        byte[] combined = Base64.getDecoder().decode(encryptedValue);
        byte[] iv = Arrays.copyOfRange(combined, 0, AES_IV_LENGTH_BYTES);
        byte[] encrypted = Arrays.copyOfRange(combined, AES_IV_LENGTH_BYTES, combined.length);
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private <T> T parseJson(String sourceJson, Class<T> targetType) {
        try {
            return objectMapper.readValue(sourceJson, targetType);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse APIM response payload.", ex);
        }
    }

    private Set<String> getRequestFields(String apiName) {
        ApiFieldEncryptionConfig config = apimProperties.getEncryption().getApis().get(normalizeApiKey(apiName));
        if (config == null || CollectionUtils.isEmpty(config.getRequestFields())) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(config.getRequestFields());
    }

    private Set<String> getResponseFields(String apiName) {
        ApiFieldEncryptionConfig config = apimProperties.getEncryption().getApis().get(normalizeApiKey(apiName));
        if (config == null || CollectionUtils.isEmpty(config.getResponseFields())) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(config.getResponseFields());
    }

    /** Extracts the last path segment to use as the YAML map key (e.g. "/ws/NGTPA/v1/TRPGetMsgBoard" -> "TRPGetMsgBoard"). */
    private String normalizeApiKey(String apiName) {
        int idx = apiName.lastIndexOf('/');
        return idx >= 0 ? apiName.substring(idx + 1) : apiName;
    }

    private byte[] hexStringToByteArray(String value) {
        int length = value.length();
        byte[] output = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            output[i / 2] = (byte) ((Character.digit(value.charAt(i), 16) << 4)
                    + Character.digit(value.charAt(i + 1), 16));
        }
        return output;
    }
}

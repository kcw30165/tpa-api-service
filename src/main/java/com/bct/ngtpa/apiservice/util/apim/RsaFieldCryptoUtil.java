package com.bct.ngtpa.apiservice.util.apim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RsaFieldCryptoUtil {
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String JWT_HEADER_JSON = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";

    private final ObjectMapper objectMapper;

    public RsaFieldCryptoUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String publicKeyEncryptPlaintext(String plainText, PublicKey publicKey) {
        if (!StringUtils.hasText(plainText)) {
            throw new IllegalArgumentException("RSA plaintext is required.");
        }

        try {
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, getOaepParameterSpec());
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt APIM AES key.", ex);
        }
    }

    public String decryptRsa(String cipherTextBase64, PrivateKey privateKey) {
        if (!StringUtils.hasText(cipherTextBase64)) {
            throw new IllegalArgumentException("RSA ciphertext is required.");
        }

        try {
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, getOaepParameterSpec());
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherTextBase64));
            return bytesToHex(decrypted);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to decrypt APIM AES key.", ex);
        }
    }

    public String createSignedJwt(String value, PrivateKey privateKey) {
        if (!StringUtils.hasText(value)) {
            return value;
        }

        try {
            String header = base64UrlEncode(JWT_HEADER_JSON.getBytes(StandardCharsets.UTF_8));
            String payload = base64UrlEncode(objectMapper.writeValueAsBytes(Map.of("value", value)));
            String signingInput = header + "." + payload;

            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signingInput + "." + base64UrlEncode(signature.sign());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign APIM field payload.", ex);
        }
    }

    public String verifyAndExtractValue(String jwt, PublicKey publicKey) {
        if (!StringUtils.hasText(jwt)) {
            return jwt;
        }

        String[] jwtParts = jwt.split("\\.");
        if (jwtParts.length != 3) {
            throw new IllegalStateException("Invalid APIM JWT format.");
        }

        try {
            String signingInput = jwtParts[0] + "." + jwtParts[1];
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            if (!signature.verify(Base64.getUrlDecoder().decode(jwtParts[2]))) {
                throw new IllegalStateException("APIM JWT signature verification failed.");
            }

            JsonNode payloadNode = objectMapper.readTree(Base64.getUrlDecoder().decode(jwtParts[1]));
            JsonNode valueNode = payloadNode.get("value");
            if (valueNode == null || valueNode.isNull()) {
                throw new IllegalStateException("APIM JWT payload does not contain value.");
            }
            return valueNode.asText();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to verify APIM field payload.", ex);
        }
    }

    private OAEPParameterSpec getOaepParameterSpec() {
        return new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT);
    }

    private String bytesToHex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (byte currentByte : value) {
            builder.append(String.format("%02x", currentByte));
        }
        return builder.toString();
    }

    private String base64UrlEncode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}

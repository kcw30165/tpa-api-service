package com.bct.ngtpa.apiservice.util.apim;

import com.bct.ngtpa.apiservice.config.ApimProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import javax.crypto.Cipher;

@Component
public class RsaFieldCryptoUtil {
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String JWT_HEADER_JSON = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";

    private final ApimProperties apimProperties;
    private final ObjectMapper objectMapper;
    private volatile PublicKey publicKey;
    private volatile PrivateKey privateKey;

    public RsaFieldCryptoUtil(ApimProperties apimProperties, ObjectMapper objectMapper) {
        this.apimProperties = apimProperties;
        this.objectMapper = objectMapper;
    }

    public String encryptKey(byte[] plainBytes) {
        return encryptKey(plainBytes, null);
    }

    public String encryptKey(byte[] plainBytes, String publicKeyMaterial) {
        if (plainBytes == null || plainBytes.length == 0) {
            throw new IllegalArgumentException("RSA plaintext bytes are required.");
        }

        try {
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, resolvePublicKey(publicKeyMaterial));
            byte[] encrypted = cipher.doFinal(plainBytes);
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt APIM AES key.", ex);
        }
    }

    public byte[] decryptKey(String cipherTextBase64) {
        if (!StringUtils.hasText(cipherTextBase64)) {
            throw new IllegalArgumentException("RSA ciphertext is required.");
        }

        try {
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getPrivateKey());
            return cipher.doFinal(Base64.getDecoder().decode(cipherTextBase64));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to decrypt APIM AES key.", ex);
        }
    }

    public String createSignedJwt(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }

        try {
            String header = base64UrlEncode(JWT_HEADER_JSON.getBytes(StandardCharsets.UTF_8));
            String payload = base64UrlEncode(objectMapper.writeValueAsBytes(Map.of("value", value)));
            String signingInput = header + "." + payload;

            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(getPrivateKey());
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));

            return signingInput + "." + base64UrlEncode(signature.sign());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign APIM field payload.", ex);
        }
    }

    public String verifyAndExtractValue(String jwt) {
        return verifyAndExtractValue(jwt, null);
    }

    public String verifyAndExtractValue(String jwt, String publicKeyMaterial) {
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
            signature.initVerify(resolvePublicKey(publicKeyMaterial));
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

    private PublicKey getPublicKey() throws Exception {
        if (publicKey == null) {
            synchronized (this) {
                if (publicKey == null) {
                    String pem = apimProperties.getEncryption().getPublicKeyPem();
                    if (!StringUtils.hasText(pem)) {
                        throw new IllegalStateException("apim.encryption.publicKeyPem is required.");
                    }
                    publicKey = parsePublicKey(pem);
                }
            }
        }
        return publicKey;
    }

    private PublicKey resolvePublicKey(String publicKeyMaterial) throws Exception {
        if (StringUtils.hasText(publicKeyMaterial)) {
            return parsePublicKey(publicKeyMaterial);
        }
        return getPublicKey();
    }

    private PrivateKey getPrivateKey() throws Exception {
        if (privateKey == null) {
            synchronized (this) {
                if (privateKey == null) {
                    String pem = apimProperties.getEncryption().getPrivateKeyPem();
                    if (!StringUtils.hasText(pem)) {
                        throw new IllegalStateException("apim.encryption.privateKeyPem is required.");
                    }
                    privateKey = parsePrivateKey(pem);
                }
            }
        }
        return privateKey;
    }

    private PublicKey parsePublicKey(String rawValue) throws Exception {
        String normalizedValue = normalizeKeyMaterial(rawValue);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        if (normalizedValue.contains("BEGIN CERTIFICATE")) {
            return CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(normalizedValue.getBytes(StandardCharsets.UTF_8)))
                    .getPublicKey();
        }

        byte[] keyBytes = decodePemOrBase64(normalizedValue);
        try {
            return CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(keyBytes))
                    .getPublicKey();
        } catch (Exception ignored) {
            return keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));
        }
    }

    private PrivateKey parsePrivateKey(String rawValue) throws Exception {
        String normalizedValue = normalizeKeyMaterial(rawValue);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        if (normalizedValue.contains("BEGIN RSA PRIVATE KEY")) {
            byte[] keyBytes = decodePemOrBase64(normalizedValue);
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(wrapPkcs1InPkcs8(keyBytes)));
        }

        byte[] keyBytes = decodePemOrBase64(normalizedValue);
        try {
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (InvalidKeySpecException ignored) {
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(wrapPkcs1InPkcs8(keyBytes)));
        }
    }

    private String normalizeKeyMaterial(String rawValue) {
        String normalizedValue = rawValue.replace("\\n", "\n").trim();
        if (normalizedValue.startsWith("-----BEGIN")) {
            return normalizedValue;
        }

        try {
            byte[] decodedBytes = Base64.getDecoder().decode(normalizedValue);
            String decodedText = new String(decodedBytes, StandardCharsets.UTF_8).trim();
            if (decodedText.startsWith("-----BEGIN")) {
                return decodedText;
            }
        } catch (IllegalArgumentException ignored) {
            return normalizedValue;
        }

        return normalizedValue;
    }

    private byte[] decodePemOrBase64(String rawValue) {
        if (rawValue.startsWith("-----BEGIN")) {
            String keyContent = rawValue
                    .replaceAll("-----BEGIN [A-Z ]+-----", "")
                    .replaceAll("-----END [A-Z ]+-----", "")
                    .replaceAll("\\s+", "");
            return Base64.getDecoder().decode(keyContent);
        }
        return Base64.getDecoder().decode(rawValue.replaceAll("\\s+", ""));
    }

    private byte[] wrapPkcs1InPkcs8(byte[] pkcs1Bytes) {
        byte[] version = new byte[] {0x02, 0x01, 0x00};
        byte[] algorithmIdentifier = new byte[] {
                0x30, 0x0D,
                0x06, 0x09,
                0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x01,
                0x05, 0x00
        };
        byte[] privateKeyOctetString = derOctetString(pkcs1Bytes);

        byte[] body = concat(version, algorithmIdentifier, privateKeyOctetString);
        return derSequence(body);
    }

    private byte[] derSequence(byte[] value) {
        return concat(new byte[] {0x30}, derLength(value.length), value);
    }

    private byte[] derOctetString(byte[] value) {
        return concat(new byte[] {0x04}, derLength(value.length), value);
    }

    private byte[] derLength(int length) {
        if (length < 128) {
            return new byte[] {(byte) length};
        }

        int temp = length;
        int byteCount = 0;
        while (temp > 0) {
            temp >>= 8;
            byteCount++;
        }

        byte[] encodedLength = new byte[1 + byteCount];
        encodedLength[0] = (byte) (0x80 | byteCount);
        for (int index = byteCount; index > 0; index--) {
            encodedLength[index] = (byte) (length & 0xFF);
            length >>= 8;
        }
        return encodedLength;
    }

    private byte[] concat(byte[]... values) {
        int totalLength = 0;
        for (byte[] value : values) {
            totalLength += value.length;
        }

        byte[] result = new byte[totalLength];
        int offset = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, result, offset, value.length);
            offset += value.length;
        }
        return result;
    }

    private String base64UrlEncode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}

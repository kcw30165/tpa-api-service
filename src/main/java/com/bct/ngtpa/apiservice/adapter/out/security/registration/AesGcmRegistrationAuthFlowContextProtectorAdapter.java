package com.bct.ngtpa.apiservice.adapter.out.security.registration;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationContactType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.registration.context.RegistrationAuthFlowEncryptedContext;
import com.bct.ngtpa.apiservice.application.port.out.RegistrationAuthFlowContextProtectorPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AesGcmRegistrationAuthFlowContextProtectorAdapter implements RegistrationAuthFlowContextProtectorPort {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int MIN_SECRET_LENGTH = 32;

    private final String keyVersion;
    private final byte[] key;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom;

    public AesGcmRegistrationAuthFlowContextProtectorAdapter(String keyVersion, String secret) {
        this(keyVersion, secret, new ObjectMapper(), new SecureRandom());
    }

    AesGcmRegistrationAuthFlowContextProtectorAdapter(
            String keyVersion,
            String secret,
            ObjectMapper objectMapper,
            SecureRandom secureRandom) {
        if (keyVersion == null || keyVersion.isBlank()) {
            throw new IllegalArgumentException("registration context protection keyVersion must not be blank");
        }
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException("registration context protection secret is missing or too short");
        }
        this.keyVersion = keyVersion.trim();
        this.key = secret.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
        this.secureRandom = secureRandom;
    }

    @Override
    public RegistrationAuthFlowEncryptedContext protect(RegistrationAuthFlowContext context) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, KEY_ALGORITHM), new GCMParameterSpec(TAG_BITS, iv));
            byte[] plaintext = objectMapper.writeValueAsBytes(toPayload(context));
            byte[] encrypted = cipher.doFinal(plaintext);
            byte[] packed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return new RegistrationAuthFlowEncryptedContext(
                    Base64.getUrlEncoder().withoutPadding().encodeToString(packed),
                    keyVersion,
                    RegistrationAuthFlowEncryptedContext.DEFAULT_ALGORITHM);
        } catch (Exception exception) {
            throw new RegistrationAuthFlowContextProtectionException("Unable to protect registration context", exception);
        }
    }

    @Override
    public RegistrationAuthFlowContext unprotect(RegistrationAuthFlowEncryptedContext encryptedContext) {
        try {
            byte[] packed = Base64.getUrlDecoder().decode(encryptedContext.ciphertext());
            if (packed.length <= IV_BYTES) {
                throw new GeneralSecurityException("invalid encrypted payload");
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[packed.length - IV_BYTES];
            System.arraycopy(packed, 0, iv, 0, IV_BYTES);
            System.arraycopy(packed, IV_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, KEY_ALGORITHM), new GCMParameterSpec(TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(encrypted);
            Payload payload = objectMapper.readValue(plaintext, Payload.class);
            return new RegistrationAuthFlowContext(
                    RegistrationIdentityType.valueOf(payload.identityType()),
                    payload.identityValue(),
                    payload.dateOfBirth(),
                    RegistrationContactType.valueOf(payload.contactType()),
                    payload.contactValue());
        } catch (Exception exception) {
            throw new RegistrationAuthFlowContextProtectionException("Unable to unprotect registration context", exception);
        }
    }

    private Payload toPayload(RegistrationAuthFlowContext context) {
        return new Payload(
                context.identityType().name(),
                context.identityValue(),
                context.dateOfBirth(),
                context.contactType().name(),
                context.contactValue());
    }

    private record Payload(
            String identityType,
            String identityValue,
            String dateOfBirth,
            String contactType,
            String contactValue) {
    }
}

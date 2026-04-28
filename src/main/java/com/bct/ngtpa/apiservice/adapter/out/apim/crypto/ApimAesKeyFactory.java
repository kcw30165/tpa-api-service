package com.bct.ngtpa.apiservice.adapter.out.apim.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class ApimAesKeyFactory {
    private static final String AES_ALGORITHM = "AES";
    private static final String ASCII_KEY_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int AES_KEY_LENGTH = 32;

    public SecretKey generateKey() {
        String asciiKey = generateAsciiKey(AES_KEY_LENGTH);
        byte[] keyBytes = asciiKey.getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(keyBytes, 0, keyBytes.length, AES_ALGORITHM);
    }

    private String generateAsciiKey(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(ASCII_KEY_CHARS.charAt(random.nextInt(ASCII_KEY_CHARS.length())));
        }
        return builder.toString();
    }
}
package com.bct.ngtpa.apiservice.application.service.auth.idempotency;

import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationIdempotencyAction;
import com.bct.ngtpa.apiservice.application.dto.auth.idempotency.RegistrationRequestHash;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

public class Sha256RegistrationRequestHashGenerator implements RegistrationRequestHashGenerator {
    @Override
    public RegistrationRequestHash hash(RegistrationIdempotencyAction action, List<String> normalizedParts) {
        Objects.requireNonNull(action, "action must not be null");
        MessageDigest digest = newDigest();
        updatePart(digest, action.keyNamespace());
        if (normalizedParts != null) {
            for (String part : normalizedParts) {
                updatePart(digest, part == null ? "" : part);
            }
        }
        return new RegistrationRequestHash(toHex(digest.digest()));
    }

    private static void updatePart(MessageDigest digest, String part) {
        byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(bytes);
        digest.update((byte) 0);
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable");
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}

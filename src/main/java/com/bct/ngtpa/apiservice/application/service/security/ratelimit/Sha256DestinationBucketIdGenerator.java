package com.bct.ngtpa.apiservice.application.service.security.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public class Sha256DestinationBucketIdGenerator implements DestinationBucketIdGenerator {

    private static final String ALGORITHM = "SHA-256";

    @Override
    public String generateBucketId(DestinationType destinationType, String destination) {
        if (destinationType == null) {
            throw new IllegalArgumentException("destination type must not be null");
        }
        String normalized = normalize(destinationType, destination);
        String domainSeparatedInput = destinationType.name() + ":" + normalized;
        return "dest_" + destinationType.name() + "_" + sha256Hex(domainSeparatedInput);
    }

    private String normalize(DestinationType destinationType, String destination) {
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("destination must not be blank");
        }
        String normalized = switch (destinationType) {
            case EMAIL -> normalizeEmail(destination);
            case MOBILE -> normalizeMobile(destination);
        };
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("destination must not be blank");
        }
        return normalized;
    }

    private String normalizeEmail(String destination) {
        return destination.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeMobile(String destination) {
        return destination.trim()
                .replace(" ", "")
                .replace("\t", "")
                .replace("\n", "")
                .replace("\r", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "");
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }
}

package com.bct.ngtpa.apiservice.adapter.out.redis;

/**
 * Deterministic Redis key factory for the NGT PA BFF.
 *
 * <p>Key format:
 * <pre>{@code <prefix>:<capability>[:<part>[:<part>...]]}</pre>
 *
 * <p>Example keys:
 * <pre>{@code
 * ngtpa:reference-date:JP
 * ngtpa:reference-date:HK
 * }</pre>
 *
 * <h3>Key safety policy</h3>
 * <ul>
 *   <li>The prefix, capability, and all parts must be non-blank.</li>
 *   <li>Raw PII — {@code policyNo}, {@code certNo}, {@code userId}, tokens,
 *       or secrets — must <em>never</em> appear as key components.</li>
 *   <li>Callers must use only opaque or environment-scoped identifiers
 *       (e.g. accountEnv codes such as "JP" or "HK").</li>
 *   <li>This class does not validate the semantic content of parts;
 *       enforcement is the caller's responsibility.</li>
 * </ul>
 */
public final class RedisCacheKeyFactory {

    private final String prefix;

    /**
     * @param prefix the application-level key prefix (e.g. {@code "ngtpa"}).
     *               Must not be blank.
     * @throws IllegalArgumentException if prefix is null or blank.
     */
    public RedisCacheKeyFactory(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException(
                    "Redis key prefix must not be blank");
        }
        this.prefix = prefix;
    }

    /**
     * Builds a Redis cache key from the given capability and optional parts.
     *
     * @param capability the functional area (e.g. {@code "reference-date"}).
     *                   Must not be blank.
     * @param parts      zero or more opaque key segments joined by {@code :}.
     *                   Each part must not be null or blank.
     * @return the composed key string.
     * @throws IllegalArgumentException if capability or any part is null or blank.
     */
    public String buildKey(String capability, String... parts) {
        if (capability == null || capability.isBlank()) {
            throw new IllegalArgumentException(
                    "Redis key capability must not be blank");
        }
        if (parts != null) {
            for (int i = 0; i < parts.length; i++) {
                if (parts[i] == null || parts[i].isBlank()) {
                    throw new IllegalArgumentException(
                            "Redis key part at index " + i + " must not be blank");
                }
            }
        }

        var sb = new StringBuilder(prefix).append(':').append(capability);
        if (parts != null) {
            for (String part : parts) {
                sb.append(':').append(part);
            }
        }
        return sb.toString();
    }
}

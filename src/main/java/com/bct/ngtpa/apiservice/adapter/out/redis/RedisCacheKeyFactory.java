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

package com.bct.ngtpa.apiservice.adapter.out.redis;

import org.springframework.util.StringUtils;

public class RedisCacheKeyFactory {
    private final String keyPrefix;

    public RedisCacheKeyFactory(String keyPrefix) {
        if (!StringUtils.hasText(keyPrefix)) {
            throw new IllegalArgumentException("Redis key prefix must not be blank");
        }
        this.keyPrefix = keyPrefix.trim();
    }

    public String key(String capability, String... parts) {
        StringBuilder builder = new StringBuilder(capabilityPrefix(capability));
        if (parts != null) {
            for (String part : parts) {
                if (!StringUtils.hasText(part)) {
                    throw new IllegalArgumentException("Redis key part must not be blank");
                }
                builder.append(':').append(part.trim());
            }
        }
        return builder.toString();
    }

    public String capabilityPrefix(String capability) {
        return keyPrefix + ':' + normalizeCapability(capability);
    }

    public String patternForCapability(String capability) {
        return capabilityPrefix(capability) + ":*";
    }

    private String normalizeCapability(String capability) {
        if (!StringUtils.hasText(capability)) {
            throw new IllegalArgumentException("Redis key capability must not be blank");
        }
        return capability.trim();
    }
}
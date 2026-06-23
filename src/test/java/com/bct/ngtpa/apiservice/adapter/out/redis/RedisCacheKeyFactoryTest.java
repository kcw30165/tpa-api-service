package com.bct.ngtpa.apiservice.adapter.out.redis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RedisCacheKeyFactoryTest {

    // ── Key format ─────────────────────────────────────────────────────────────

    @Test
    void capabilityOnlyKeyFollowsPrefixColonCapabilityFormat() {
        var factory = new RedisCacheKeyFactory("ngtpa");
        assertEquals("ngtpa:reference-date", factory.key("reference-date"));
    }

    @Test
    void singlePartKeyFollowsPrefixColonCapabilityColonPartFormat() {
        var factory = new RedisCacheKeyFactory("ngtpa");
        assertEquals("ngtpa:reference-date:JP", factory.key("reference-date", "JP"));
    }

    @Test
    void multiplePartsAreJoinedByColonSeparator() {
        var factory = new RedisCacheKeyFactory("ngtpa");
        assertEquals("ngtpa:cache:JP:2026-05", factory.key("cache", "JP", "2026-05"));
    }

    @Test
    void prefixIsIncludedAtStartOfKey() {
        var factory = new RedisCacheKeyFactory("myapp");
        String key = factory.key("capability", "part");
        assertTrue(key.startsWith("myapp:"), "Key must start with configured prefix");
    }

    // ── Prefix validation ─────────────────────────────────────────────────────

    @Test
    void blankPrefixThrowsIllegalArgumentExceptionAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new RedisCacheKeyFactory(""));
    }

    @Test
    void whitespacePrefixThrowsIllegalArgumentExceptionAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new RedisCacheKeyFactory("   "));
    }

    @Test
    void nullPrefixThrowsIllegalArgumentExceptionAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new RedisCacheKeyFactory(null));
    }

    // ── Capability validation ─────────────────────────────────────────────────

    @Test
    void blankCapabilityThrowsIllegalArgumentException() {
        var factory = new RedisCacheKeyFactory("ngtpa");
        assertThrows(IllegalArgumentException.class, () -> factory.key(""));
    }

    @Test
    void whitespaceCapabilityThrowsIllegalArgumentException() {
        var factory = new RedisCacheKeyFactory("ngtpa");
        assertThrows(IllegalArgumentException.class, () -> factory.key("  "));
    }

    @Test
    void nullCapabilityThrowsIllegalArgumentException() {
        var factory = new RedisCacheKeyFactory("ngtpa");
        assertThrows(IllegalArgumentException.class, () -> factory.key(null));
    }

    // ── Part validation ───────────────────────────────────────────────────────

    @Test
    void blankFirstPartThrowsIllegalArgumentException() {
        var factory = new RedisCacheKeyFactory("ngtpa");
        assertThrows(IllegalArgumentException.class,
                () -> factory.key("reference-date", ""));
    }

    @Test
    void whitespaceFirstPartThrowsIllegalArgumentException() {
        var factory = new RedisCacheKeyFactory("ngtpa");
        assertThrows(IllegalArgumentException.class,
                () -> factory.key("reference-date", "  "));
    }

    @Test
    void nullFirstPartThrowsIllegalArgumentException() {
        var factory = new RedisCacheKeyFactory("ngtpa");
        assertThrows(IllegalArgumentException.class,
                () -> factory.key("reference-date", (String) null));
    }

    @Test
    void blankSecondPartThrowsIllegalArgumentException() {
        var factory = new RedisCacheKeyFactory("ngtpa");
        assertThrows(IllegalArgumentException.class,
                () -> factory.key("reference-date", "JP", ""));
    }

    @Test
    void nullSecondPartThrowsIllegalArgumentException() {
        var factory = new RedisCacheKeyFactory("ngtpa");
        assertThrows(IllegalArgumentException.class,
                () -> factory.key("reference-date", "JP", null));
    }

    // ── Determinism ───────────────────────────────────────────────────────────

    @Test
    void sameInputsAlwaysProduceSameKey() {
        var factory = new RedisCacheKeyFactory("ngtpa");
        assertEquals(factory.key("reference-date", "JP"),
                factory.key("reference-date", "JP"));
    }

    @Test
    void differentPrefixesProduceDifferentKeysForSameCapabilityAndPart() {
        var factory1 = new RedisCacheKeyFactory("app1");
        var factory2 = new RedisCacheKeyFactory("app2");
        assertNotEquals(factory1.key("reference-date", "JP"),
                factory2.key("reference-date", "JP"));
    }

    // ── Key safety policy ─────────────────────────────────────────────────────

    @Test
    void keyWithEnvironmentCodeDoesNotContainPiiFieldNames() {
        // Documents the key-safety policy:
        // Keys may only use opaque, env-scoped identifiers (e.g. accountEnv codes).
        // Raw PII — policyNo, certNo, userId — must never appear as key components.
        // The factory does not enforce this; callers are responsible.
        //
        // A correct example: accountEnv code is a non-PII environment tag.
        var factory = new RedisCacheKeyFactory("ngtpa");
        String key = factory.key("reference-date", "JP");

        assertEquals("ngtpa:reference-date:JP", key);
        assertFalse(key.contains("policy"), "Key must not embed a policy number");
        assertFalse(key.contains("cert"), "Key must not embed a cert number");
        assertFalse(key.contains("user"), "Key must not embed a user ID");
        assertFalse(key.contains("token"), "Key must not embed a token or secret");
    }
}

package com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Task 019 opaque Scheme-Ref generator: secure-random, unique, opaque values with
 * at least 16 bytes of entropy (base64url of 24 random bytes), never parsed for business
 * meaning, and never disclosed by the value object's {@code toString}.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-solution-design.md}
 * section 6 step 11 (opaque Scheme-Ref creation only when a session is created),
 * {@code docs/design/6-username-recovery/username-recovery-test-traceability-design.md}
 * section 6 (Scheme-Ref entropy at least 16 bytes), and UR-R12 (opaque and session-bound).
 */
class UsernameRecoveryStartSchemeRefGeneratorTest {

    private final UsernameRecoveryStartSchemeRefGenerator generator =
            new UsernameRecoveryStartSchemeRefGenerator();

    @Test
    void generatesOpaqueUniqueValuesWithSufficientEntropy() {
        UsernameRecoverySchemeRef first = generator.generate();
        UsernameRecoverySchemeRef second = generator.generate();

        assertThat(first.value()).matches("^sch_[A-Za-z0-9_-]{32}$");
        assertThat(second.value()).matches("^sch_[A-Za-z0-9_-]{32}$");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void generatesDistinctValuesAcrossManyCalls() {
        Set<UsernameRecoverySchemeRef> generated = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            generated.add(generator.generate());
        }
        assertThat(generated).hasSize(1000);
    }

    @Test
    void valueObjectNeverDisclosesTheRawSchemeRef() {
        UsernameRecoverySchemeRef schemeRef = generator.generate();

        assertThat(schemeRef.toString()).doesNotContain(schemeRef.value());
    }
}

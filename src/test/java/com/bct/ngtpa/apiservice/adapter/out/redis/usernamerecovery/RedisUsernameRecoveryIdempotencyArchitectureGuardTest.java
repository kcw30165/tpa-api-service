package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyAction;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyKey;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoveryIdempotencyPort;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Group 4: architecture and shared-script reuse guards for the Username Recovery
 * idempotency Redis adapter.
 *
 * <p>Pins: reserve reuses the generic native SET-NX convention (no feature Lua);
 * the single retained feature script (transition.lua) exists only because the shared
 * auth-flow-session scripts use whole-value CAS and the workflow-state sidecar script
 * is revision-based (physically incompatible); the adapter implements the typed
 * reactive port; application/domain never depend on the adapter; Redis keys stay
 * opaque and feature-scoped.
 */
class RedisUsernameRecoveryIdempotencyArchitectureGuardTest {

    @Test
    void reserveNeedsNoFeatureScriptBecauseItUsesGenericSetNx() throws IOException {
        assertThat(new ClassPathResource(
                "redis/username-recovery-idempotency/reserve.lua").exists()).isFalse();
        assertThat(new ClassPathResource(
                "redis/username-recovery-idempotency/resolve.lua").exists()).isFalse();
    }

    @Test
    void transitionRetainsFeatureScriptBecauseSharedScriptsArePhysicallyIncompatible()
            throws IOException {
        var sharedReplace = resource("redis/auth-flow-session/replace-revisioned.lua");
        var workflowStateUpdate = resource("redis/username-recovery-workflow-state/update.lua");
        var feature = resource("redis/username-recovery-idempotency/transition.lua");

        // Shared auth-flow-session replacement is a WHOLE-VALUE CAS: it requires the
        // exact current JSON, which the idempotency adapter never knows.
        assertThat(sharedReplace)
                .contains("current ~= ARGV[1]")
                .doesNotContain("decoded.action");
        // Username Recovery workflow-state update is revision-field based; idempotency
        // records have no revision field.
        assertThat(workflowStateUpdate)
                .contains("decoded.revision")
                .doesNotContain("decoded.operationReference");

        assertThat(feature)
                .contains("decoded.action")
                .contains("decoded.idempotencyKey")
                .contains("decoded.fingerprint")
                .contains("decoded.operationReference")
                .contains("decoded.status")
                .contains("PTTL")
                .contains("PSETEX")
                .doesNotContain("call('EXPIRE'")
                .doesNotContain("call('PEXPIRE'");
    }

    @Test
    void adapterImplementsTypedReactivePort() {
        assertThat(UsernameRecoveryIdempotencyPort.class.isAssignableFrom(
                RedisUsernameRecoveryIdempotencyAdapter.class)).isTrue();
    }

    @Test
    void applicationAndDomainNeverDependOnRedisUsernameRecoveryIdempotencyAdapter() {
        JavaClasses classes = new ClassFileImporter().importPackages(
                "com.bct.ngtpa.apiservice.application.port.out",
                "com.bct.ngtpa.apiservice.domain.model");
        ArchRuleDefinition.noClasses()
                .that().resideInAnyPackage("..application.port.out..", "..domain.model..")
                .should().dependOnClassesThat()
                .resideInAPackage("..adapter.out.redis.usernamerecovery..")
                .check(classes);
    }

    @Test
    void redisKeyStaysOpaqueAndFeatureScoped() {
        var key = RedisUsernameRecoveryIdempotencyKeyFactory.keyFor(
                new RedisCacheKeyFactory("ngtpa"),
                UsernameRecoveryIdempotencyAction.REQUEST,
                new UsernameRecoveryIdempotencyKey("idem_12345678"));
        assertThat(key).isEqualTo(
                "ngtpa:username-recovery:idempotency:username-recovery-request:idem_12345678");
        assertThat(key)
                .doesNotContain("user-sch")
                .doesNotContain("member@example.com")
                .doesNotContain("csrf")
                .doesNotContain("cookie")
                .doesNotContain("policy")
                .doesNotContain("cert");
    }

    private static String resource(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}

package com.bct.ngtpa.apiservice.adapter.out.redis.usernamerecovery;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.application.port.out.UsernameRecoverySessionPort;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionId;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Pins shared-script reuse and feature-specific CAS/delete compatibility decisions. */
class RedisUsernameRecoverySessionArchitectureGuardTest {

    @Test
    void createReusesSharedRevisionedScript() throws IOException {
        var script = resource("redis/auth-flow-session/create-revisioned.lua");
        assertThat(script)
                .contains("EXISTS")
                .contains("PSETEX")
                .contains("ARGV[1]")
                .contains("ARGV[2]");
        assertThat(new ClassPathResource(
                "redis/username-recovery-session/create.lua").exists()).isFalse();
    }

    @Test
    void updateRetainsFeatureScriptBecauseSharedReplacementUsesWholeValueCas()
            throws IOException {
        var shared = resource("redis/auth-flow-session/replace-revisioned.lua");
        var feature = resource("redis/username-recovery-session/update.lua");

        assertThat(shared)
                .contains("current ~= ARGV[1]")
                .contains("PTTL")
                .contains("PSETEX")
                .doesNotContain("decoded.revision");
        assertThat(feature)
                .contains("decoded.revision")
                .contains("PTTL")
                .contains("PSETEX")
                .contains("return 0")
                .contains("return -1")
                .doesNotContain("call('EXPIRE'")
                .doesNotContain("call('PEXPIRE'");
    }

    @Test
    void deleteRetainsFeatureScriptBecauseSharedDeletionRequiresExpectedWholeValue()
            throws IOException {
        var shared = resource("redis/auth-flow-session/delete-revisioned.lua");
        var feature = resource("redis/username-recovery-session/delete.lua");

        assertThat(shared).contains("current ~= ARGV[1]");
        assertThat(feature)
                .contains("EXISTS")
                .contains("DEL")
                .doesNotContain("ARGV[1]");
    }

    @Test
    void applicationAndDomainNeverDependOnRedisUsernameRecoveryAdapter() {
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
    void adapterImplementsTypedReactivePort() {
        assertThat(UsernameRecoverySessionPort.class.isAssignableFrom(
                RedisUsernameRecoverySessionAdapter.class)).isTrue();
    }

    @Test
    void redisKeyStaysOpaqueAndFeatureScoped() {
        var key = RedisUsernameRecoverySessionKeyFactory.keyFor(
                new RedisCacheKeyFactory("ngtpa"),
                new UsernameRecoverySessionId("urs_opaque-1"));
        assertThat(key).isEqualTo("ngtpa:username-recovery:session:urs_opaque-1");
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

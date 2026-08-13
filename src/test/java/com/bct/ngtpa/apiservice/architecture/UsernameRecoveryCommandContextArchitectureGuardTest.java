package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.MemberIdentityType;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartCommand;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Repair 018A command-context architecture and redaction guard.
 *
 * <p>Verifies that the three Username Recovery application commands (Start, Request, Cancel):
 *
 * <ul>
 *   <li>stay inside the application DTO package with no Spring/adapter/config/infrastructure/Redis
 *       dependency;</li>
 *   <li>carry the raw client idempotency key as a typed record component (Start also carries the
 *       normalized {@link Locale} request language);</li>
 *   <li>never carry a raw CSRF field (Task 015 boundary preserved);</li>
 *   <li>never carry a semantic fingerprint field, so the client key and the fingerprint stay
 *       distinct;</li>
 *   <li>redact sensitive values, session ids, Scheme-Refs, and idempotency keys in
 *       {@code toString()}.</li>
 * </ul>
 */
class UsernameRecoveryCommandContextArchitectureGuardTest {

    @Test
    void usernameRecoveryCommandsCarryNoRawCsrfAndKeepClientKeyDistinctFromFingerprint() {
        List<Class<?>> commands = List.of(
                UsernameRecoveryStartCommand.class,
                UsernameRecoveryRequestCommand.class,
                UsernameRecoveryCancelCommand.class);

        for (Class<?> command : commands) {
            List<String> componentNames = componentNames(command);
            assertThat(componentNames).as(command.getSimpleName()).noneMatch(
                    name -> name.toLowerCase(Locale.ROOT).contains("csrf"));
            assertThat(componentNames).as(command.getSimpleName()).doesNotContain("fingerprint");
            assertThat(componentNames).as(command.getSimpleName()).contains("idempotencyKey");
        }

        assertThat(componentNames(UsernameRecoveryStartCommand.class)).contains("language");
    }

    @Test
    void usernameRecoveryCommandsStayFrameworkAndAdapterFree() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery");

        ArchRule rule = noClasses()
                .that().resideInAPackage("..application.dto.auth.usernamerecovery..")
                .and().haveSimpleNameEndingWith("Command")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.servlet..",
                        "com.bct.ngtpa.apiservice.adapter..",
                        "com.bct.ngtpa.apiservice.config..",
                        "com.bct.ngtpa.apiservice.infrastructure..",
                        "io.lettuce..",
                        "org.springframework.data.redis..")
                .as("Username Recovery commands must stay framework-free and adapter-free");
        rule.check(classes);
    }

    @Test
    void usernameRecoveryCommandToStringRedactsSensitiveValuesAndIdempotencyKeys() {
        var start = new UsernameRecoveryStartCommand(
                MemberIdentityType.STAFFNO, "3030609", "04/07/1978", "member@example.com", "66290597",
                "cap_secret", Locale.forLanguageTag("zh-HK"), "idem_start_secret");
        var request = new UsernameRecoveryRequestCommand(
                new AuthFlowSessionId("afs_secret"), new UsernameRecoverySchemeRef("sch_secret"),
                "idem_request_secret");
        var cancel = new UsernameRecoveryCancelCommand(
                new AuthFlowSessionId("afs_secret"), "idem_cancel_secret");

        assertThat(start.toString())
                .doesNotContain("3030609", "04/07/1978", "member@example.com", "66290597", "cap_secret",
                        "idem_start_secret")
                .contains("language=", "idempotencyKey=");
        assertThat(start.toString()).doesNotContain("zh_HK", "zh-HK");
        assertThat(request.toString())
                .doesNotContain("afs_secret", "sch_secret", "idem_request_secret")
                .contains("idempotencyKey=");
        assertThat(cancel.toString())
                .doesNotContain("afs_secret", "idem_cancel_secret")
                .contains("idempotencyKey=");
    }

    private static List<String> componentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
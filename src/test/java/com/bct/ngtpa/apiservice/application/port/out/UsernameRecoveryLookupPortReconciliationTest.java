package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryLookupResult;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Task 018 reconciliation of the committed Username Recovery lookup outbound port.
 *
 * <p>Confirms the single committed {@link UsernameRecoveryLookupPort} is reused (no duplicate
 * port, alias, facade, or repository), exposes exactly one typed reactive {@code search}
 * operation whose signature crosses only application DTOs plus Reactor, and is implemented by
 * exactly one production class confined to the APIM outbound adapter.
 *
 * <p>These tests validate the committed Task 009 contract. A pass therefore records
 * {@code EXPECTED_EXISTING_CONTRACT_CONFIRMED}; a failure would indicate a genuine predecessor
 * defect and require escalation, never an automatic production rewrite.
 */
class UsernameRecoveryLookupPortReconciliationTest {

    @Test
    void exactlyOneProductionLookupPortInterfaceExists() {
        List<JavaClass> named = productionClasses().stream()
                .filter(javaClass -> javaClass.getSimpleName().equals("UsernameRecoveryLookupPort"))
                .toList();

        assertThat(named).hasSize(1);
        assertThat(named.get(0).getName()).isEqualTo(UsernameRecoveryLookupPort.class.getName());
        assertThat(UsernameRecoveryLookupPort.class.isInterface()).isTrue();
    }

    @Test
    void exactlyOneProductionImplementationExistsInsideTheApimAdapter() {
        List<JavaClass> implementors = productionClasses().stream()
                .filter(javaClass -> !javaClass.isInterface())
                .filter(javaClass -> javaClass.isAssignableTo(UsernameRecoveryLookupPort.class))
                .toList();

        assertThat(implementors).hasSize(1);
        assertThat(implementors.get(0).getName()).isEqualTo(
                "com.bct.ngtpa.apiservice.adapter.out.apim.usernamerecovery.TrpSearchMemberAdapter");
    }

    @Test
    void portExposesExactlyOneSearchOperationWithTypedCommandAndResult() throws Exception {
        Method[] methods = UsernameRecoveryLookupPort.class.getDeclaredMethods();

        assertThat(methods).hasSize(1);
        assertThat(Modifier.isAbstract(methods[0].getModifiers())).isTrue();
        assertThat(methods[0].getName()).isEqualTo("search");

        Method search = UsernameRecoveryLookupPort.class.getMethod(
                "search", UsernameRecoveryLookupCommand.class);
        assertThat(search.getParameterTypes()).containsExactly(UsernameRecoveryLookupCommand.class);
        assertThat(search.getReturnType()).isEqualTo(Mono.class);
        assertThat(search.getGenericReturnType().getTypeName()).isEqualTo(
                Mono.class.getName() + "<" + UsernameRecoveryLookupResult.class.getName() + ">");
    }

    @Test
    void portSignatureContainsNoTransportOrGenericTypes() throws Exception {
        Method search = UsernameRecoveryLookupPort.class.getMethod(
                "search", UsernameRecoveryLookupCommand.class);

        String signature = search.toGenericString();
        assertThat(signature)
                .contains("Mono<", "UsernameRecoveryLookupResult", "UsernameRecoveryLookupCommand");

        assertThat(signature)
                .doesNotContain("java.lang.Object", "java.util.Map", "java.util.HashMap")
                .doesNotContain(
                        ".adapter.", "org.springframework", "org.springframework.web",
                        "org.springframework.http", "org.springframework.data.redis",
                        "io.lettuce", "reactor.netty", "WebClient", "Apim", "Redis",
                        "HttpStatus", "HttpHeaders");
    }

    private static com.tngtech.archunit.core.domain.JavaClasses productionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice");
    }
}

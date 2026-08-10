package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryCookieWriter;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryRequestDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoverySchemeResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryStartDataResponse;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.UsernameRecoveryWorkflowResultResponse;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryCancelCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryRequestCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartCommand;
import com.bct.ngtpa.apiservice.application.port.in.CancelUsernameRecoveryUseCase;
import com.bct.ngtpa.apiservice.application.port.in.RequestUsernameRecoveryUseCase;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Repair 024A controller-prerequisite architecture guard.
 *
 * <p>
 * Locks: no Username Recovery controller class is added by this repair; the
 * {@link UsernameRecoveryCookieWriter} stays Spring-free and
 * instruction-producing; the richer
 * Request/Cancel inbound-port signatures are the single abstract methods with
 * transient String
 * CSRF/Origin/Referer parameters; command records carry no raw
 * CSRF/Origin/Referer; and no public
 * web DTO gains session id, cookie, CSRF hash, username, or raw destination
 * fields.
 */
class UsernameRecoveryControllerPrerequisiteArchitectureGuardTest {

        private static final List<String> FORBIDDEN_DTO_FIELDS = List.of(
                        "sessionId", "authFlowSessionId", "csrfHash", "cookie", "cookieName",
                        "cookiePath", "username", "rawDestination", "requestId");

        private static final List<String> FORBIDDEN_COMMAND_FIELDS = List.of(
                        "rawCsrfToken", "csrfToken", "origin", "referer");

        @Test
        void noUsernameRecoveryControllerClassIsAdded() {
                var classes = new ClassFileImporter()
                                .withImportOption(new ImportOption.DoNotIncludeTests())
                                .importPackages("com.bct.ngtpa.apiservice");

                ArchRule rule = classes()
                                .that().resideInAPackage("..adapter.in.web..")
                                .and().haveSimpleNameContaining("UsernameRecovery")
                                .and().haveSimpleNameEndingWith("Controller")
                                .should().beInterfaces()
                                .allowEmptyShould(true)
                                .as("Repair 024A must not implement any Task 024 Username Recovery controller");
                rule.check(classes);
        }

        @Test
        void cookieWriterStaysSpringFreeAndProducesFrameworkFreeInstructions() {
                var classes = new ClassFileImporter()
                                .withImportOption(new ImportOption.DoNotIncludeTests())
                                .importPackages("com.bct.ngtpa.apiservice");

                ArchRule rule = noClasses()
                                .that().haveSimpleName("UsernameRecoveryCookieWriter")
                                .should().dependOnClassesThat().resideInAnyPackage(
                                                "com.bct.ngtpa.apiservice.adapter.out..",
                                                "com.bct.ngtpa.apiservice.config..",
                                                "com.bct.ngtpa.apiservice.infrastructure..",
                                                "org.springframework..",
                                                "org.springframework.http..",
                                                "org.springframework.web..",
                                                "org.springframework.web.reactive..",
                                                "reactor.core..",
                                                "jakarta.servlet..",
                                                "io.lettuce..",
                                                "org.springframework.data.redis..",
                                                "java.lang.reflect..")
                                .as("UsernameRecoveryCookieWriter must stay Spring-free and produce framework-free "
                                                + "SessionCookieInstruction values");
                rule.check(classes);
        }

        @Test
        void inboundPortsExposeRicherRequestAndCancelSignaturesAsSingleAbstractMethods() {
                assertSingleAbstractMethodWithTransientSecurityParameters(
                                RequestUsernameRecoveryUseCase.class, "request");
                assertSingleAbstractMethodWithTransientSecurityParameters(
                                CancelUsernameRecoveryUseCase.class, "cancel");
        }

        @Test
        void commandRecordsCarryNoRawCsrfOriginOrReferer() {
                for (Class<?> command : List.of(
                                UsernameRecoveryStartCommand.class,
                                UsernameRecoveryRequestCommand.class,
                                UsernameRecoveryCancelCommand.class)) {
                        for (Field field : command.getDeclaredFields()) {
                                assertThat(field.getName())
                                                .as("field in %s", command.getSimpleName())
                                                .isNotIn(FORBIDDEN_COMMAND_FIELDS);
                        }
                }
        }

        @Test
        void publicWebDtosDoNotGainForbiddenFields() {
                for (Class<?> dto : List.of(
                                UsernameRecoveryStartDataResponse.class,
                                UsernameRecoveryRequestDataResponse.class,
                                UsernameRecoveryWorkflowResultResponse.class,
                                UsernameRecoverySchemeResponse.class)) {
                        for (Field field : dto.getDeclaredFields()) {
                                assertThat(field.getName())
                                                .as("field in %s", dto.getSimpleName())
                                                .isNotIn(FORBIDDEN_DTO_FIELDS);
                        }
                }
        }

        private static void assertSingleAbstractMethodWithTransientSecurityParameters(
                        Class<?> port, String methodName) {
                List<Method> abstractMethods = Arrays.stream(port.getDeclaredMethods())
                                .filter(method -> Modifier.isAbstract(method.getModifiers()))
                                .toList();
                assertThat(abstractMethods).as(port.getSimpleName()).hasSize(1);
                Method method = abstractMethods.get(0);
                assertThat(method.getName()).isEqualTo(methodName);
                assertThat(method.getParameterCount()).isEqualTo(4);
                assertThat(Arrays.asList(method.getParameterTypes()).subList(1, 4))
                                .containsExactly(String.class, String.class, String.class);

                List<Method> defaultMethods = Arrays.stream(port.getDeclaredMethods())
                                .filter(candidate -> !Modifier.isAbstract(candidate.getModifiers()))
                                .filter(candidate -> !candidate.isBridge() && !candidate.isSynthetic())
                                .toList();
                assertThat(defaultMethods).as(port.getSimpleName() + " compatibility defaults").hasSize(1);
                assertThat(defaultMethods.get(0).getParameterCount()).isEqualTo(1);
        }
}

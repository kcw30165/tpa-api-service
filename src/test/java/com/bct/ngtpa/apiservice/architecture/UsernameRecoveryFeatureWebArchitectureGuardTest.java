package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.mapper.UsernameRecoveryWorkflowMapping;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryCancelResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryRequestResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryResponsePolicy;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.usernamerecovery.policy.UsernameRecoveryStartResponsePolicy;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Task 023 architecture guard for the Username Recovery feature response-policy boundary.
 *
 * <p>Locks:
 *
 * <ul>
 *   <li>the five response-policy and workflow-mapping classes stay free of outbound adapters,
 *       config, infrastructure, Spring, servlet, Reactor, Redis, and reflection types
 *       (controller/cookie/transport concerns stay in Task 024);
 *   <li>no all-purpose {@code UsernameRecoveryHttpResponses} type exists in the feature web
 *       package;
 *   <li>the Task 023 policy and mapping classes declare no raw {@code Map} or {@code Object}
 *       members (no reflection, raw maps, or generic {@code Object} contracts).
 * </ul>
 */
class UsernameRecoveryFeatureWebArchitectureGuardTest {

  private static final String FEATURE_PACKAGE = "..adapter.in.web.auth.usernamerecovery..";

  private static final List<Class<?>> POLICY_CLASSES =
      List.of(
          UsernameRecoveryResponsePolicy.class,
          UsernameRecoveryWorkflowMapping.class,
          UsernameRecoveryStartResponsePolicy.class,
          UsernameRecoveryRequestResponsePolicy.class,
          UsernameRecoveryCancelResponsePolicy.class);

  @Test
  void featurePolicyClassesNeverDependOnFrameworkTransportOutboundOrReflectionTypes() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice");

    DescribedPredicate<JavaClass> policyOrMappingClass =
        new DescribedPredicate<>("the five Username Recovery policy and workflow mapping classes") {
          @Override
          public boolean test(JavaClass input) {
            return POLICY_CLASSES.stream().anyMatch(type -> type.getName().equals(input.getName()));
          }
        };

    ArchRule rule =
        noClasses()
            .that(policyOrMappingClass)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
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
            .as(
                "Username Recovery feature response policies must stay free of outbound adapters, "
                    + "config, infrastructure, Spring, servlet, Reactor, Redis, and reflection types");
    rule.check(classes);
  }

  @Test
  void noAllPurposeUsernameRecoveryHttpResponsesTypeExistsInFeatureWebPackage() {
    var classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.bct.ngtpa.apiservice");

    ArchRule rule =
        noClasses()
            .that()
            .resideInAPackage(FEATURE_PACKAGE)
            .should()
            .haveSimpleName("UsernameRecoveryHttpResponses")
            .as("Task 023 must not create an all-purpose UsernameRecoveryHttpResponses type");
    rule.check(classes);
  }

  @Test
  void policyClassesDeclareNoRawMapObjectOrUncheckedCastMembers() {
    for (Class<?> type : POLICY_CLASSES) {
      for (Field field : type.getDeclaredFields()) {
        assertThat(field.getType())
            .as("field %s in %s", field.getName(), type.getSimpleName())
            .isNotEqualTo(Object.class)
            .isNotEqualTo(Map.class);
      }
      for (Method method : type.getDeclaredMethods()) {
        if (isObjectRootMethod(method)) {
          continue;
        }
        assertThat(method.getReturnType())
            .as("return type of %s in %s", method.getName(), type.getSimpleName())
            .isNotEqualTo(Object.class)
            .isNotEqualTo(Map.class);
        for (Class<?> parameterType : method.getParameterTypes()) {
          assertThat(parameterType)
              .as("parameter of %s in %s", method.getName(), type.getSimpleName())
              .isNotEqualTo(Object.class)
              .isNotEqualTo(Map.class);
        }
      }
    }
  }

  private static boolean isObjectRootMethod(Method method) {
    String name = method.getName();
    return name.equals("equals") || name.equals("hashCode") || name.equals("toString");
  }
}

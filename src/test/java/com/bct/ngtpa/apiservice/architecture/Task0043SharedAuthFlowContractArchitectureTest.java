package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowIdempotencyPort;
import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowMessagePort;
import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowOtpPort;
import com.bct.ngtpa.apiservice.application.port.out.authflow.AuthFlowProtectedContextPort;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(
    packages = "com.bct.ngtpa.apiservice",
    importOptions = ImportOption.DoNotIncludeTests.class)
class Task0043SharedAuthFlowContractArchitectureTest {

  @Test
  void sharedAuthFlowContractsAreFrameworkNeutralApplicationPorts() {
    assertThat(AuthFlowOtpPort.class.getPackageName()).endsWith("application.port.out.authflow");
    assertThat(AuthFlowMessagePort.class.getPackageName())
        .endsWith("application.port.out.authflow");
    assertThat(AuthFlowIdempotencyPort.class.getPackageName())
        .endsWith("application.port.out.authflow");
    assertThat(AuthFlowProtectedContextPort.class.getPackageName())
        .endsWith("application.port.out.authflow");
  }

  @ArchTest
  static final ArchRule sharedContractsMustNotDependOnRegistrationOrAdapters =
      noClasses()
          .that()
          .resideInAPackage("..application.port.out.authflow..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..auth.registration..", "..adapter..", "org.springframework..")
          .as("shared auth-flow contracts must be scenario-neutral and framework-free");
}

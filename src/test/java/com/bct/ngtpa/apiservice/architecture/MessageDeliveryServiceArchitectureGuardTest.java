package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "com.bct.ngtpa.apiservice", importOptions = ImportOption.DoNotIncludeTests.class)
class MessageDeliveryServiceArchitectureGuardTest {

    @ArchTest
    static final ArchRule messageDeliveryServiceDoesNotDependOnOtpRedisWebApimOrMail = noClasses()
            .that().haveFullyQualifiedName("com.bct.ngtpa.apiservice.application.usecase.message.MessageDeliveryAdapter")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort",
                    "com.bct.ngtpa.apiservice.application.port.out.OtpChallengeStatePort",
                    "com.bct.ngtpa.apiservice.adapter..",
                    "org.springframework..",
                    "io.lettuce..",
                    "org.springframework.data.redis..",
                    "jakarta.mail..",
                    "javax.mail..",
                    "org.springframework.mail..",
                    "org.springframework.web.reactive.function.client..");

    @Test
    void messageDeliveryServiceDoesNotDependOnOtpRedisWebApimOrMail() {
        messageDeliveryServiceDoesNotDependOnOtpRedisWebApimOrMail.check(new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice"));
    }
}
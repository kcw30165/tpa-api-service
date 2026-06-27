package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "com.bct.ngtpa.apiservice", importOptions = ImportOption.DoNotIncludeTests.class)
class MessageTemplateRendererArchitectureGuardTest {

    @ArchTest
    static final ArchRule messageTemplateRendererHasNoSpringAdapterMailWebClientOrRedisDependencies = noClasses()
            .that().resideInAnyPackage("..application.service.message..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "com.bct.ngtpa.apiservice.adapter..",
                    "io.lettuce..",
                    "org.springframework.data.redis..",
                    "jakarta.mail..",
                    "javax.mail..",
                    "org.springframework.mail..",
                    "org.springframework.web.reactive.function.client..");

    @Test
    void rendererHasNoSpringAdapterMailWebClientOrRedisDependencies() {
        messageTemplateRendererHasNoSpringAdapterMailWebClientOrRedisDependencies.check(new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice"));
    }
}
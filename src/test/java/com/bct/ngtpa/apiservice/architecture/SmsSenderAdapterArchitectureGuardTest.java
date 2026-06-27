package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "com.bct.ngtpa.apiservice", importOptions = ImportOption.DoNotIncludeTests.class)
class SmsSenderAdapterArchitectureGuardTest {

    @ArchTest
    static final ArchRule smsProviderDtosDoNotLeakOutsideSmsAdapter = noClasses()
            .that().resideOutsideOfPackage("..adapter.out.sms..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out.sms.dto..");

    @ArchTest
    static final ArchRule smsAdapterDoesNotDependOnApimOrWebAdapters = noClasses()
            .that().resideInAnyPackage("..adapter.out.sms..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapter.out.apim..",
                    "..adapter.in.web..");

    @Test
    void smsProviderDtosDoNotLeakOutsideSmsAdapter() {
        smsProviderDtosDoNotLeakOutsideSmsAdapter.check(new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice"));
    }

    @Test
    void smsAdapterDoesNotDependOnApimOrWebAdapters() {
        smsAdapterDoesNotDependOnApimOrWebAdapters.check(new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice"));
    }
}
package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "com.bct.ngtpa.apiservice", importOptions = ImportOption.DoNotIncludeTests.class)
class EmailSenderAdapterArchitectureGuardTest {

    @ArchTest
    static final ArchRule emailProviderTypesDoNotLeakOutsideEmailAdapter = noClasses()
            .that().resideOutsideOfPackage("..adapter.out.email..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out.email..");

    @ArchTest
    static final ArchRule emailAdapterDoesNotDependOnApimSmsOrWebAdapters = noClasses()
            .that().resideInAnyPackage("..adapter.out.email..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..adapter.out.apim..",
                    "..adapter.out.sms..",
                    "..adapter.in.web..");

    @Test
    void emailProviderTypesDoNotLeakOutsideEmailAdapter() {
        emailProviderTypesDoNotLeakOutsideEmailAdapter.check(new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice"));
    }

    @Test
    void emailAdapterDoesNotDependOnApimSmsOrWebAdapters() {
        emailAdapterDoesNotDependOnApimSmsOrWebAdapters.check(new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice"));
    }
}

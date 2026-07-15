package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaptchaGeneratorArchitectureGuardTest {

    @Test
    void captchaGeneratorAdaptersHaveNoWebRedisApimSmsEmailDependencies() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice.adapter.out.captcha");

        noClasses()
                .that()
                .resideInAPackage("..adapter.out.captcha..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "com.bct.ngtpa.apiservice.adapter.in.web..",
                        "com.bct.ngtpa.apiservice.adapter.out.redis..",
                        "com.bct.ngtpa.apiservice.adapter.out.apim..",
                        "com.bct.ngtpa.apiservice.adapter.out.sms..",
                        "com.bct.ngtpa.apiservice.adapter.out.email..",
                        "org.springframework.web..",
                        "org.springframework.http..",
                        "jakarta.servlet..",
                        "io.lettuce..",
                        "org.springframework.data.redis..").check(classes);
    }

    @Test
    void captchaGeneratorDoesNotHardcodeConfirmedPolicyValues() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/captcha/SecureRandomCaptchaTextGenerator.java"));
        assertThat(source).doesNotContain("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789");
    }

    @Test
    void captchaGeneratorDoesNotIntroduceRedisAdapterAndAllowsWebEndpoints() throws Exception {
        assertClassAbsent("com.bct.ngtpa.apiservice.adapter.out.redis.CaptchaChallengeRedisAdapter");
        assertClassPresent("com.bct.ngtpa.apiservice.adapter.in.web.controller.AuthCaptchaController");
    }

    private static void assertClassAbsent(String className) throws Exception {
        try {
            Class.forName(className);
            throw new AssertionError(className + " should not exist");
        } catch (ClassNotFoundException expected) {
            assertThat(expected).isNotNull();
        }
    }

    private static void assertClassPresent(String className) throws Exception {
        assertThat(Class.forName(className)).isNotNull();
    }

    private static boolean sourceContains(String text) throws Exception {
        Path root = Path.of("src/main");
        if (!Files.exists(root)) {
            return false;
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                    .anyMatch(path -> contains(path, text));
        }
    }

    private static boolean contains(Path path, String text) {
        try {
            return Files.readString(path).contains(text);
        } catch (Exception exception) {
            return false;
        }
    }
}
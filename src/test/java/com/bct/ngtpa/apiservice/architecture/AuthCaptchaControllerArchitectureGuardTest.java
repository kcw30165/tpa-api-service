package com.bct.ngtpa.apiservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuthCaptchaControllerArchitectureGuardTest {

    @Test
    void captchaControllerDoesNotDependOnOutboundAdapters() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.bct.ngtpa.apiservice.adapter.in.web.controller");

        noClasses()
                .that()
                .haveSimpleName("AuthCaptchaController")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..adapter.out.redis..",
                        "..adapter.out.apim..",
                        "..adapter.out.sms..",
                        "..adapter.out.email..",
                        "org.springframework.data.redis..",
                        "io.lettuce..",
                        "..application.port.out..").check(classes);
    }

    @Test
    void captchaUseCaseConfigMayDependOnPortsButNotAdapters() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/bct/ngtpa/apiservice/config/captcha/CaptchaChallengeUseCaseConfig.java"));

        assertThat(source)
                .contains("AuthWorkflowPolicyPort")
                .contains("CaptchaChallengePort")
                .doesNotContain("AuthWorkflowProperties")
                .doesNotContain("adapter.out.redis")
                .doesNotContain("RedisCaptchaChallengeAdapter")
                .doesNotContain("ReactiveRedisTemplate");
    }

    @Test
    void captchaConfigDoesNotIntroduceSecondCaptchaRoot() throws Exception {
        Path root = Path.of("src/main/resources");
        if (!Files.exists(root)) {
            return;
        }
        try (var files = Files.walk(root)) {
            var content = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                    .map(this::readSilently)
                    .reduce("", (left, right) -> left + "\n" + right);

            assertThat(content).doesNotContainPattern("(?m)^captcha\\s*:")
                    .doesNotContainPattern("(?m)^auth-captcha\\s*:");
        }
    }

    private String readSilently(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception exception) {
            return "";
        }
    }
}
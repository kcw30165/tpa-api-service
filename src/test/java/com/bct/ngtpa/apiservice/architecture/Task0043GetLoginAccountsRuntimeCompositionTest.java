package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class Task0043GetLoginAccountsRuntimeCompositionTest {
    @Test
    void accountAdapterConfigurationComposesCanonicalUseCase() throws Exception {
        Path config;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            config = files
                    .filter(path -> path.getFileName().toString().equals("TrpGetAccountListAdapterConfig.java"))
                    .findFirst()
                    .orElseThrow();
        }
        String source = Files.readString(config).replaceAll("\\s+", "");
        assertThat(source).contains(
                "@BeanGetLoginAccountsUseCasegetLoginAccountsUseCase(LoginAccountAuthorizationPortauthorizationPort)",
                "returnnewGetLoginAccountsUseCase(authorizationPort);");
        assertThat(source).doesNotContain("@ComponentclassGetLoginAccountsUseCase", "@ServiceclassGetLoginAccountsUseCase");
    }

    @Test
    void applicationUseCaseRemainsFrameworkNeutral() throws Exception {
        Path useCase;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            useCase = files
                    .filter(path -> path.getFileName().toString().equals("GetLoginAccountsUseCase.java"))
                    .findFirst()
                    .orElseThrow();
        }
        String source = Files.readString(useCase);
        assertThat(source)
                .contains("LoginAccountAuthorizationPort")
                .doesNotContain("org.springframework", "@Component", "@Service", "@Bean");
    }
}

package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LoginCleanArchitectureCorrectionTest {

    @Test
    void loginApplicationPackagesContainNoSpringConfigurationOrAdapterImports() throws Exception {
        Path root = Path.of("src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login");
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                assertThat(source)
                        .as(path.toString())
                        .doesNotContain(
                                "org.springframework",
                                "@Configuration",
                                "@Bean",
                                "com.bct.ngtpa.apiservice.adapter.");
            }
        }
    }

    @Test
    void loginCompositionLivesInOuterConfigLayer() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/config/auth/login/LoginApplicationConfig.java"));
        assertThat(source).contains(
                "@Configuration",
                "LoginCredentialIdempotencyCoordinator",
                "StartLoginUseCase",
                "AuthWorkflowProperties",
                "AuthFlowSessionProperties");
    }

    @Test
    void loginApimTransportDoesNotDependOnCryptoImplementationPackage() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/login/SharedApimTrpMemberLoginTransport.java"));
        assertThat(source).doesNotContain(
                "adapter.out.apim.crypto",
                "ApimCryptoException");
        assertThat(source).contains("ApimPayloadCryptoService");
    }

    @Test
    void privateHelperIsNotNamedExecute() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/start/StartLoginService.java"));
        assertThat(source).doesNotContain("private Mono<StartLoginResult> execute(");
        assertThat(source).contains("authenticateAndEstablish(");
    }
}

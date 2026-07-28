package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RegistrationProtectedAuthFlowContextLoaderArchitectureGuardTest {
    @Test
    void protectedContextLoaderImplementationStaysFrameworkFree() throws Exception {
        Path sourcePath = Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/service/auth/registration/context/DefaultRegistrationProtectedAuthFlowContextLoader.java");
        assertThat(Files.exists(sourcePath)).isTrue();
        String source = Files.readString(sourcePath);
        assertThat(source).doesNotContain("org.springframework", "adapter.in.web", "adapter.out.redis",
                "adapter.out.apim", "ServerWebExchange", "Cookie");
    }
}

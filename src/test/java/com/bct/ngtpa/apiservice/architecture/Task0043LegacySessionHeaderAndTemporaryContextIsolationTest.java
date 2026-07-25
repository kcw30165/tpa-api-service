package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task0043LegacySessionHeaderAndTemporaryContextIsolationTest {
    @Test
    void productionUsesOnlyCanonicalSidXxxHeaderConstant() throws Exception {
        String keys = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/shared/web/RequestHeaderContextKeys.java"));
        String main = Files.walk(Path.of("src/main/java"))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .map(path -> {
                    try { return Files.readString(path); }
                    catch (Exception exception) { throw new RuntimeException(exception); }
                })
                .reduce("", String::concat);
        assertThat(keys).contains("AUTHENTICATED_SESSION_HEADER = \"sid_xxx\"")
                .doesNotContain(
                        "public static final String SESSION_ID_HEADER =",
                        "\"Session-Id\"");
        assertThat(main).doesNotContain("RequestHeaderContextKeys." + "SESSION_ID_HEADER");
    }

    @Test
    void temporaryYamlAdapterIsNotAProductionSpringComponent() throws Exception {
        String temporary = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/out/security/TemporaryPortalAccessContextAdapter.java"));
        String config = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/config/auth/session/AuthenticatedSessionConfig.java"));
        assertThat(temporary).doesNotContain("@Component", "org.springframework.stereotype.Component");
        assertThat(config).contains("@Primary", "AuthenticatedAccountContextResolver");
    }
}

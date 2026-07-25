package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Task0043AuthenticatedRequestContextFilterArchitectureTest {
    @Test
    void filterUsesCanonicalSidTransportAndTwoArgumentResolution() throws Exception {
        String s = Files.readString(Path
                .of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/filter/PortalAccessContextWebFilter.java"));
        assertThat(s.replaceAll("\\s+", ""))
                .contains("AUTHENTICATED_SESSION_HEADER", "resolvePortalAccessContext(accountRef,sessionId)")
                .doesNotContain("SESSION_ID_HEADER");
    }

    @Test
    void temporaryAdapterIsNotInjectedDirectly() throws Exception {
        String s = Files.readString(Path
                .of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/filter/PortalAccessContextWebFilter.java"));
        assertThat(s).contains("PortalAccessContextPort").doesNotContain("TemporaryPortalAccessContextAdapter", "Redis",
                "AuthenticatedSessionPort");
    }
}

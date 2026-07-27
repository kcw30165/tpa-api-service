package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Task0043AuthenticatedAccountContextResolutionArchitectureTest {
    @Test
    void resolverUsesLifecycleAndExistingPortalPort() throws Exception {
        String s = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/session/authenticated/AuthenticatedAccountContextResolver.java"));
        assertThat(s)
                .contains("implements PortalAccessContextPort", "LoadAndTouchAuthenticatedSessionUseCase",
                        "PortalAccessContext", "AccountContext")
                .doesNotContain("Redis", "ObjectMapper", "ServerWebExchange", "Cookie");
    }

    @Test
    void oneArgumentTransitionalResolutionIsRejected() throws Exception {
        String resolver = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/session/authenticated/AuthenticatedAccountContextResolver.java"))
                .replaceAll("\\s+", "");
        String failure = Files.readString(Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/session/authenticated/AuthenticatedPortalContextResolutionException.java"));
        assertThat(resolver).contains("resolvePortalAccessContext(StringaccountRef,StringsessionId)",
                "resolvePortalAccessContext(StringaccountRef){returnMono.error(newAuthenticatedPortalContextResolutionException())");
        assertThat(failure).contains("Authenticated account context unavailable");
    }
}

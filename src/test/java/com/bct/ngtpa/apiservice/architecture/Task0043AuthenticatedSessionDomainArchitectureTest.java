package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class Task0043AuthenticatedSessionDomainArchitectureTest {
    @Test
    void modelIsFrameworkAndInfrastructureNeutral() throws Exception {
        String root = "src/main/java/com/bct/ngtpa/apiservice/application/dto/session/authenticated/";
        String all = Files.walk(Path.of(root)).filter(Files::isRegularFile).map(p -> {
            try {
                return Files.readString(p);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).reduce("", String::concat);
        assertThat(all).contains("AuthenticatedSession", "AuthenticatedSessionAccount", "ActorContext").doesNotContain(
                "org.springframework", "Redis", "WebClient", "ObjectMapper", "Cipher", "ServerHttp", "Cookie");
    }

    @Test
    void actorContextHasNoActorTypeAndSessionHasNoPortalTransportHeader() {
        String actor = read("src/main/java/com/bct/ngtpa/apiservice/application/dto/ActorContext.java");
        String session = read(
                "src/main/java/com/bct/ngtpa/apiservice/application/dto/session/authenticated/AuthenticatedSession.java");
        assertThat(actor).doesNotContain("actorType");
        assertThat(session).doesNotContain("Session-Id", "Account-Ref", "X-Authenticated-Session-Id");
    }

    private String read(String p) {
        try {
            return Files.readString(Path.of(p));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

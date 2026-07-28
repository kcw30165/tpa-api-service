package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.logout.AuthenticatedLogoutWebMapper;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.logout.config.AuthenticatedLogoutWebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class Task0043AuthenticatedLogoutWebCompositionTest {
    @Test
    void logoutWebMapperIsExplicitlyComposed() {
        try (var context = new AnnotationConfigApplicationContext(AuthenticatedLogoutWebConfig.class)) {
            assertThat(context.getBean(AuthenticatedLogoutWebMapper.class))
                    .isExactlyInstanceOf(AuthenticatedLogoutWebMapper.class);
        }
    }

    @Test
    void mapperRemainsFreeOfComponentScanningAnnotations() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/logout/AuthenticatedLogoutWebMapper.java"));
        assertThat(source).doesNotContain("@Component", "@Service");
    }
}

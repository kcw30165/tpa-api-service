package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.*;
import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.adapter.in.web.workflow.WorkflowPublicApiStatusNormalizer;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PublicDownstreamStatusIsolationGuardTest {
    @Test
    void technicalDownstreamStatusNormalizesToSystemError() {
        assertThat(WorkflowPublicApiStatusNormalizer.normalize(ApiStatus.DOWNSTREAM_ERROR))
                .isEqualTo(ApiStatus.SYSTEM_ERROR);
    }

    @Test
    void recognizedDownstreamBusinessRejectionUsesApprovedBusinessStatus() {
        assertThat(WorkflowPublicApiStatusNormalizer.normalize(ApiStatus.DOWNSTREAM_REJECTED))
                .isEqualTo(ApiStatus.BUSINESS_REJECTED);
    }

    @Test
    void publicWebCodeDoesNotExposeDownstreamApiStatusTokens() throws Exception {
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/in/web"))) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path);
                            if (source.contains("ApiStatus.DOWNSTREAM_ERROR")
                                    || source.contains("ApiStatus.DOWNSTREAM_REJECTED")) {
                                violations.add(path.toString());
                            }
                        } catch (Exception error) {
                            throw new RuntimeException(error);
                        }
                    });
        }
        assertThat(violations).isEmpty();
    }
}

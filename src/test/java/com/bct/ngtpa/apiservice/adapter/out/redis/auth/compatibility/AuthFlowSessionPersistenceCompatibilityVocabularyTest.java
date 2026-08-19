package com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility;

import static org.assertj.core.api.Assertions.*;

import com.bct.ngtpa.apiservice.application.port.out.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AuthFlowSessionPersistenceCompatibilityVocabularyTest {
  @Test
  void applicationBoundaryUsesOnlyApplicationTypes() {
    assertThat(AuthFlowSessionPersistenceCompatibilityPort.class.getPackageName())
        .startsWith("com.bct.ngtpa.apiservice.application");
    assertThat(
            Arrays.stream(AuthFlowSessionPersistenceCompatibilityPort.class.getMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes())))
        .noneMatch(type -> type.getName().contains("redis") || type.getName().contains("spring"));
  }

  @Test
  void resultProvidesApprovedSafeStatuses() {
    assertThat(AuthFlowSessionPersistenceCompatibilityStatus.values())
        .containsExactly(
            AuthFlowSessionPersistenceCompatibilityStatus.COMPATIBLE,
            AuthFlowSessionPersistenceCompatibilityStatus.INITIALIZED,
            AuthFlowSessionPersistenceCompatibilityStatus.CONFIGURATION_VERSION_UNSUPPORTED,
            AuthFlowSessionPersistenceCompatibilityStatus.EXISTING_VERSION_MISMATCH,
            AuthFlowSessionPersistenceCompatibilityStatus.MARKER_CORRUPT,
            AuthFlowSessionPersistenceCompatibilityStatus.STORAGE_UNAVAILABLE,
            AuthFlowSessionPersistenceCompatibilityStatus.INITIALIZATION_CONFLICT,
            AuthFlowSessionPersistenceCompatibilityStatus.SYSTEM_FAILURE);
  }
}

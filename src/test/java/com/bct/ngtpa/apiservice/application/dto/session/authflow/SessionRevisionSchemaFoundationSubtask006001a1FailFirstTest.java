package com.bct.ngtpa.apiservice.application.dto.session.authflow;

import static org.assertj.core.api.Assertions.*;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SessionRevisionSchemaFoundationSubtask006001a1FailFirstTest {
  @Test
  void revisionIsOpaqueFrameworkFreeAndRedacted() {
    var revision = new SessionRevision("0123456789abcdef0123456789abcdef");
    assertThat(revision.toString()).isEqualTo("SessionRevision[value=<redacted>]");
    assertThat(SessionRevision.class.getAnnotations()).isEmpty();
    assertThatThrownBy(() -> new SessionRevision(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new SessionRevision(" ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void secureGeneratorProvidesDistinct128BitTokens() {
    var generator = new SecureSessionRevisionGenerator();
    var first = generator.generate();
    var second = generator.generate();
    assertThat(first).isNotEqualTo(second);
    assertThat(first.value()).matches("[0-9a-f]{32}");
    assertThat(second.value()).matches("[0-9a-f]{32}");
  }

  @Test
  void snapshotContainsOnlySessionAndRevision() {
    assertThat(
            Arrays.stream(AuthFlowSessionSnapshot.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly("session", "revision");
  }
}

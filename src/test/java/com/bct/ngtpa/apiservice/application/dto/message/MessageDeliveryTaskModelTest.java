package com.bct.ngtpa.apiservice.application.dto.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageDeliveryTaskModelTest {

  @Test
  void messageDeliveryTaskIsPassiveAndFrameworkFree() {
    MessageDeliveryCommand command =
        new MessageDeliveryCommand(
            MessageDeliveryType.REGISTRATION_OTP,
            MessageDeliveryScenario.REGISTRATION,
            MessageDeliveryChannel.SMS,
            "91234567",
            null,
            "Hello {name}",
            null,
            Map.of("name", "Member"),
            "en",
            "req-123");
    Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

    MessageDeliveryTask task = new MessageDeliveryTask("task-123", command, createdAt, "req-123");

    assertThat(task.taskId()).isEqualTo("task-123");
    assertThat(task.command()).isSameAs(command);
    assertThat(task.createdAt()).isEqualTo(createdAt);
    assertThat(task.correlationId()).isEqualTo("req-123");
    assertThat(MessageDeliveryTaskStatus.values())
        .containsExactly(
            MessageDeliveryTaskStatus.PENDING,
            MessageDeliveryTaskStatus.PROCESSING,
            MessageDeliveryTaskStatus.COMPLETED,
            MessageDeliveryTaskStatus.FAILED);

    assertThat(
            Arrays.stream(MessageDeliveryTask.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly("taskId", "command", "createdAt", "correlationId");
    assertThat(
            Arrays.stream(MessageDeliveryTask.class.getRecordComponents())
                .map(component -> component.getType().getName()))
        .noneMatch(name -> name.startsWith("org.springframework"))
        .noneMatch(name -> name.startsWith("com.bct.ngtpa.apiservice.adapter"))
        .noneMatch(name -> name.contains("Provider"))
        .noneMatch(name -> name.contains("Payload"));
    assertThat(Arrays.stream(MessageDeliveryTask.class.getDeclaredMethods()).map(Method::getName))
        .doesNotContain("enqueue", "persist", "retry", "schedule", "send");
  }
}

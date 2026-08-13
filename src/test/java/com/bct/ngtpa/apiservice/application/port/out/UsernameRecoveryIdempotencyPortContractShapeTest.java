package com.bct.ngtpa.apiservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Verifies the Username Recovery idempotency outbound-port contract shape: typed reactive
 * operations for reserve, resolve/replay, commit, and stable unknown outcome; framework-free
 * contract types; command/result invariants; and redaction of key, fingerprint, replay payload,
 * and operation reference.
 *
 * <p>Design references: {@code docs/design/6-username-recovery/username-recovery-session-redis-design.md}
 * sections 4 and 8 and the Task 013 outcome matrix (evidence {@code 004-outcome-matrix.txt}).
 * Mirrors {@code RegistrationIdempotencyPortContractShapeTest} and
 * {@code UsernameRecoverySessionPortContractShapeTest} conventions.
 */
class UsernameRecoveryIdempotencyPortContractShapeTest {

    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
    private static final String DIGEST =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void exposesTypedReactiveLifecycleOperations() throws Exception {
        assertMonoMethod("reserve", UsernameRecoveryIdempotencyPort.ReserveCommand.class,
                UsernameRecoveryIdempotencyPort.ReservationResult.class);
        assertResolveMethod();
        assertMonoMethod("complete", UsernameRecoveryIdempotencyPort.CompleteCommand.class,
                UsernameRecoveryIdempotencyRecord.class);
        assertMonoMethod("recordUnknownOutcome",
                UsernameRecoveryIdempotencyPort.UnknownOutcomeCommand.class,
                UsernameRecoveryIdempotencyRecord.class);
    }

    @Test
    void portContractTypesAreFrameworkFree() throws Exception {
        List<Type> contractTypes = new ArrayList<>();
        for (Method method : UsernameRecoveryIdempotencyPort.class.getDeclaredMethods()) {
            if (method.isSynthetic() || method.isBridge()) {
                continue;
            }
            contractTypes.add(method.getReturnType());
            contractTypes.addAll(List.of(method.getGenericParameterTypes()));
        }
        for (Type type : contractTypes) {
            assertAllowedType(type);
        }
    }

    @Test
    void reserveCommandRequiresPositiveTtlAndComputesExpiry() {
        assertThatThrownBy(() -> new UsernameRecoveryIdempotencyPort.ReserveCommand(
                UsernameRecoveryIdempotencyAction.REQUEST, key(), fingerprint(), operationReference(),
                NOW, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ttl must be positive");
        assertThatThrownBy(() -> new UsernameRecoveryIdempotencyPort.ReserveCommand(
                null, key(), fingerprint(), operationReference(), NOW, Duration.ofMinutes(5)))
                .isInstanceOf(NullPointerException.class);

        UsernameRecoveryIdempotencyPort.ReserveCommand command =
                new UsernameRecoveryIdempotencyPort.ReserveCommand(
                        UsernameRecoveryIdempotencyAction.REQUEST, key(), fingerprint(),
                        operationReference(), NOW, Duration.ofMinutes(5));
        assertThat(command.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void reservationResultAllowsReservationOnlyWhenReserved() {
        UsernameRecoveryIdempotencyReservation reservation = reservation();
        assertThat(new UsernameRecoveryIdempotencyPort.ReservationResult(
                UsernameRecoveryIdempotencyOutcome.RESERVED, reservation).reservation())
                .isSameAs(reservation);

        for (UsernameRecoveryIdempotencyOutcome outcome : List.of(
                UsernameRecoveryIdempotencyOutcome.DUPLICATE_IN_PROGRESS,
                UsernameRecoveryIdempotencyOutcome.COMPLETED_REPLAY,
                UsernameRecoveryIdempotencyOutcome.KEY_REUSED_WITH_DIFFERENT_REQUEST,
                UsernameRecoveryIdempotencyOutcome.UNKNOWN_OUTCOME_EXISTING,
                UsernameRecoveryIdempotencyOutcome.SYSTEM_FAILURE)) {
            assertThatThrownBy(() -> new UsernameRecoveryIdempotencyPort.ReservationResult(
                    outcome, reservation))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("non-reserved outcome must not carry a reservation");
        }
        assertThatThrownBy(() -> new UsernameRecoveryIdempotencyPort.ReservationResult(
                UsernameRecoveryIdempotencyOutcome.RESERVED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reserved outcome requires a reservation");
    }

    @Test
    void unknownOutcomeIsStableAndNeverACompletedReplay() {
        assertThat(UsernameRecoveryIdempotencyRecord.Status.UNKNOWN_OUTCOME)
                .isNotEqualTo(UsernameRecoveryIdempotencyRecord.Status.COMPLETED);
        assertThat(UsernameRecoveryIdempotencyRecord.Status.values())
                .contains(UsernameRecoveryIdempotencyRecord.Status.UNKNOWN_OUTCOME);
    }

    @Test
    void commandsAndResultsRedactSensitiveValues() {
        UsernameRecoveryIdempotencyPort.ReserveCommand reserve =
                new UsernameRecoveryIdempotencyPort.ReserveCommand(
                        UsernameRecoveryIdempotencyAction.REQUEST,
                        new UsernameRecoveryIdempotencyKey("idem_secret"),
                        new UsernameRecoveryIdempotencyFingerprint(DIGEST),
                        new UsernameRecoveryIdempotencyOperationReference("op-secret"),
                        NOW, Duration.ofMinutes(5));
        UsernameRecoveryIdempotencyPort.CompleteCommand complete =
                new UsernameRecoveryIdempotencyPort.CompleteCommand(
                        UsernameRecoveryIdempotencyAction.REQUEST,
                        new UsernameRecoveryIdempotencyKey("idem_secret"),
                        new UsernameRecoveryIdempotencyFingerprint(DIGEST),
                        new UsernameRecoveryIdempotencyOperationReference("op-secret"),
                        new UsernameRecoveryIdempotencyRecord.ReplaySnapshot("snap-secret"),
                        NOW, Duration.ofMinutes(5));
        UsernameRecoveryIdempotencyPort.UnknownOutcomeCommand unknown =
                new UsernameRecoveryIdempotencyPort.UnknownOutcomeCommand(
                        UsernameRecoveryIdempotencyAction.REQUEST,
                        new UsernameRecoveryIdempotencyKey("idem_secret"),
                        new UsernameRecoveryIdempotencyFingerprint(DIGEST),
                        new UsernameRecoveryIdempotencyOperationReference("op-secret"),
                        NOW, Duration.ofMinutes(5));
        UsernameRecoveryIdempotencyPort.ReservationResult result =
                new UsernameRecoveryIdempotencyPort.ReservationResult(
                        UsernameRecoveryIdempotencyOutcome.RESERVED, reservation());

        assertThat(reserve.toString()).doesNotContain("idem_secret", DIGEST, "op-secret");
        assertThat(complete.toString()).doesNotContain("idem_secret", DIGEST, "op-secret", "snap-secret");
        assertThat(unknown.toString()).doesNotContain("idem_secret", DIGEST, "op-secret");
        assertThat(result.toString())
                .contains("outcome=RESERVED")
                .doesNotContain("idem_secret", DIGEST, "op-secret");
    }

    private static void assertMonoMethod(
            String name, Class<?> parameter, Class<?> resultType) throws Exception {
        Method method = UsernameRecoveryIdempotencyPort.class.getMethod(name, parameter);
        assertThat(method.getReturnType()).isEqualTo(Mono.class);
        ParameterizedType returnType = (ParameterizedType) method.getGenericReturnType();
        assertThat(returnType.getActualTypeArguments()).containsExactly(resultType);
    }

    private static void assertResolveMethod() throws Exception {
        Method method = UsernameRecoveryIdempotencyPort.class.getMethod(
                "resolve", UsernameRecoveryIdempotencyAction.class, UsernameRecoveryIdempotencyKey.class);
        assertThat(method.getReturnType()).isEqualTo(Mono.class);
        ParameterizedType returnType = (ParameterizedType) method.getGenericReturnType();
        assertThat(returnType.getActualTypeArguments()).hasSize(1);
        Type argument = returnType.getActualTypeArguments()[0];
        assertThat(argument).isInstanceOf(ParameterizedType.class);
        ParameterizedType optionalType = (ParameterizedType) argument;
        assertThat(optionalType.getRawType()).isEqualTo(Optional.class);
        assertThat(optionalType.getActualTypeArguments())
                .containsExactly(UsernameRecoveryIdempotencyRecord.class);
    }

    private static void assertAllowedType(Type type) {
        if (type instanceof ParameterizedType parameterizedType) {
            assertAllowedType(parameterizedType.getRawType());
            for (Type argument : parameterizedType.getActualTypeArguments()) {
                assertAllowedType(argument);
            }
            return;
        }
        assertThat(type.getTypeName())
                .as("port contract type %s must stay inside application/framework-free types", type)
                .satisfiesAnyOf(
                        name -> assertThat(name).startsWith("java."),
                        name -> assertThat(name).startsWith("reactor."),
                        name -> assertThat(name)
                                .startsWith("com.bct.ngtpa.apiservice.application.port.out."));
    }

    private static UsernameRecoveryIdempotencyKey key() {
        return new UsernameRecoveryIdempotencyKey("idem_12345678");
    }

    private static UsernameRecoveryIdempotencyFingerprint fingerprint() {
        return new UsernameRecoveryIdempotencyFingerprint(DIGEST);
    }

    private static UsernameRecoveryIdempotencyOperationReference operationReference() {
        return new UsernameRecoveryIdempotencyOperationReference("op-1234567890");
    }

    private static UsernameRecoveryIdempotencyReservation reservation() {
        return new UsernameRecoveryIdempotencyReservation(
                UsernameRecoveryIdempotencyAction.REQUEST,
                key(),
                fingerprint(),
                operationReference(),
                NOW.plusSeconds(300));
    }
}

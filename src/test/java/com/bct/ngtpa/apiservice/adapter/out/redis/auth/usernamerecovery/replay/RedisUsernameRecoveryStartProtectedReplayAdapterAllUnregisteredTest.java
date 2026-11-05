package com.bct.ngtpa.apiservice.adapter.out.redis.auth.usernamerecovery.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoverySchemeProjection;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.UsernameRecoveryStartResult;
import com.bct.ngtpa.apiservice.application.dto.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowScenario;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSession;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionData;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionId;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowSessionSnapshot;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.AuthFlowStatus;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.SessionRevision;
import com.bct.ngtpa.apiservice.application.idempotency.ProtectedReplayReference;
import com.bct.ngtpa.apiservice.application.port.in.CreateAuthFlowSessionCsrfUseCase;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import com.bct.ngtpa.apiservice.application.service.auth.usernamerecovery.context.UsernameRecoveryAuthFlowContextBinding;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.ProtectedUsernameRecoveryStartReplay;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayProtectorPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayReferenceGenerator;
import com.bct.ngtpa.apiservice.application.usecase.auth.usernamerecovery.idempotency.start.UsernameRecoveryStartProtectedReplayState;
import com.bct.ngtpa.apiservice.application.usecase.session.authflow.CreateAuthFlowSessionCsrfResult;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySchemeRef;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoverySessionCandidate;
import com.bct.ngtpa.apiservice.domain.model.UsernameRecoveryWorkflowStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * The protected START replay rebuilds every candidate row without filtering, so an all-unregistered
 * session replays as the same SESSION_CREATED result with registered=false preserved.
 */
@SuppressWarnings("unchecked")
class RedisUsernameRecoveryStartProtectedReplayAdapterAllUnregisteredTest {
  private static final Instant NOW = Instant.parse("2026-11-05T02:00:00Z");

  private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
  private final ReactiveValueOperations<String, String> values =
      mock(ReactiveValueOperations.class);
  private final RedisUsernameRecoveryStartProtectedReplayKeyFactory keys =
      mock(RedisUsernameRecoveryStartProtectedReplayKeyFactory.class);
  private final RedisUsernameRecoveryStartProtectedReplayJsonMapper json =
      mock(RedisUsernameRecoveryStartProtectedReplayJsonMapper.class);
  private final UsernameRecoveryStartProtectedReplayProtectorPort protector =
      mock(UsernameRecoveryStartProtectedReplayProtectorPort.class);
  private final UsernameRecoveryStartProtectedReplayReferenceGenerator references =
      mock(UsernameRecoveryStartProtectedReplayReferenceGenerator.class);
  private final AuthFlowSessionPort sessions = mock(AuthFlowSessionPort.class);
  private final UsernameRecoveryAuthFlowContextBinding contexts =
      mock(UsernameRecoveryAuthFlowContextBinding.class);
  private final CreateAuthFlowSessionCsrfUseCase csrf =
      mock(CreateAuthFlowSessionCsrfUseCase.class);

  private final AuthFlowSessionId sid = new AuthFlowSessionId("afs-unamerec-1");
  private final ProtectedReplayReference reference =
      new ProtectedReplayReference("replay-ref-1234567890abcd");

  @Test
  void anAllUnregisteredSessionReplaysAsSessionCreatedWithTheUnregisteredFlagPreserved() {
    Instant expiry = NOW.plusSeconds(300);
    when(redis.opsForValue()).thenReturn(values);
    when(keys.keyFor(any())).thenReturn("redis-key-1");
    when(values.get(any())).thenReturn(Mono.just("{}"));
    when(json.fromJson(any())).thenReturn(mock(ProtectedUsernameRecoveryStartReplay.class));
    when(protector.unprotect(any(), any()))
        .thenReturn(new UsernameRecoveryStartProtectedReplayState(sid, expiry));
    var session =
        new AuthFlowSession(
            sid,
            AuthFlowScenario.USERNAME_RECOVERY,
            AuthFlowStatus.STARTED,
            "csrfhash",
            null,
            new AuthFlowSessionData("email", "j***@example.com", Map.of()),
            NOW.minusSeconds(120),
            NOW.minusSeconds(60),
            expiry);
    when(sessions.loadSnapshot(any()))
        .thenReturn(
            Mono.just(
                Optional.of(
                    new AuthFlowSessionSnapshot(session, new SessionRevision("0".repeat(32))))));
    var context = mock(UsernameRecoveryAuthFlowContext.class);
    when(context.workflowStatus()).thenReturn(UsernameRecoveryWorkflowStatus.ACTIVE);
    var first = candidate("sch_ref_1", "OI", "JPM");
    var second = candidate("sch_ref_2", "DB", "DBA");
    when(context.candidates()).thenReturn(List.of(first, second));
    when(contexts.load(any())).thenReturn(context);
    when(csrf.createForSession(any()))
        .thenReturn(Mono.just(new CreateAuthFlowSessionCsrfResult("csrf-raw-token", expiry, 300)));

    var adapter =
        new RedisUsernameRecoveryStartProtectedReplayAdapter(
            redis,
            keys,
            json,
            protector,
            references,
            sessions,
            contexts,
            csrf,
            Clock.fixed(NOW, ZoneOffset.UTC));

    StepVerifier.create(adapter.resolve(reference))
        .assertNext(
            result -> {
              assertThat(result.outcome())
                  .isEqualTo(UsernameRecoveryStartResult.Outcome.SESSION_CREATED);
              assertThat(result.authFlowSessionId()).isEqualTo(sid);
              assertThat(result.csrfToken()).isEqualTo("csrf-raw-token");
              assertThat(result.schemes())
                  .extracting(UsernameRecoverySchemeProjection::trustCode)
                  .containsExactly("JPM", "DBA");
              assertThat(result.schemes())
                  .allSatisfy(scheme -> assertThat(scheme.registered()).isFalse());
              assertThat(result.schemes())
                  .extracting(scheme -> scheme.schemeRef().value())
                  .containsExactly("sch_ref_1", "sch_ref_2");
            })
        .verifyComplete();
  }

  private static UsernameRecoverySessionCandidate candidate(
      String ref, String env, String trustCode) {
    var candidate = mock(UsernameRecoverySessionCandidate.class);
    when(candidate.schemeRef()).thenReturn(new UsernameRecoverySchemeRef(ref));
    when(candidate.accountEnv()).thenReturn(env);
    when(candidate.trustCode()).thenReturn(trustCode);
    when(candidate.registered()).thenReturn(false);
    return candidate;
  }
}

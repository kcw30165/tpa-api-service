package com.bct.ngtpa.apiservice.adapter.out.redis.auth.revision;

import static org.assertj.core.api.Assertions.*;
import com.bct.ngtpa.apiservice.adapter.out.redis.RedisCacheKeyFactory;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.*;
import com.bct.ngtpa.apiservice.adapter.out.redis.auth.compatibility.AuthFlowSessionPersistenceCompatibilityState;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.CachePort;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class RedisAuthFlowSessionRevisionMutationAdapterTest {
    private static final Instant NOW=Instant.parse("2026-08-01T00:00:00Z");

    @Test void successfulUpdateAdvancesRevisionAndPreservesExpiry(){
        var f=new Fixture(); var expected=f.store();
        var updated=copy(expected.session(),AuthFlowStatus.OTP_VERIFIED,NOW.plusSeconds(30));
        var result=f.adapter.updateRevisioned(new RevisionedAuthFlowSessionMutationCommand(expected,updated)).block();
        assertThat(result.outcome()).isEqualTo(AuthFlowSessionMutationOutcome.APPLIED);
        assertThat(result.snapshot().revision()).isNotEqualTo(expected.revision());
        assertThat(result.snapshot().session().expiresAt()).isEqualTo(expected.session().expiresAt());
    }

    @Test void staleWriterAndDeleteConflictPerformZeroMutation(){
        var f=new Fixture(); var expected=f.store(); String before=f.cache.value; f.ops.code=0;
        var result=f.adapter.touchRevisioned(expected,NOW.plusSeconds(10)).block();
        assertThat(result.outcome()).isEqualTo(AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED);
        assertThat(f.cache.value).isEqualTo(before);
        var deleted=f.adapter.deleteRevisioned(new RevisionedAuthFlowSessionDeleteCommand(expected)).block();
        assertThat(deleted.outcome()).isEqualTo(AuthFlowSessionMutationOutcome.SESSION_REVISION_CHANGED);
        assertThat(f.cache.value).isEqualTo(before);
    }

    @Test void terminalCompleteAndCancelAreRejectedWithoutRedisMutation(){
        var f=new Fixture(); var terminal=new AuthFlowSessionSnapshot(
                copy(session(),AuthFlowStatus.COMPLETED,NOW),rev("a"));
        assertThat(f.adapter.completeRevisioned(terminal,NOW.plusSeconds(1)).block().outcome())
                .isEqualTo(AuthFlowSessionMutationOutcome.INVALID_TRANSITION);
        assertThat(f.adapter.cancelRevisioned(terminal,NOW.plusSeconds(1)).block().outcome())
                .isEqualTo(AuthFlowSessionMutationOutcome.INVALID_TRANSITION);
        assertThat(f.ops.replaceCalls).isZero();
    }

    private static SessionRevision rev(String c){return new SessionRevision(c.repeat(32));}
    private static AuthFlowSession session(){return new AuthFlowSession(new AuthFlowSessionId("afs_test"),
            AuthFlowScenario.LOGIN,AuthFlowStatus.OTP_SENT,"csrf","otp",AuthFlowSessionData.empty(),
            NOW,NOW,NOW.plusSeconds(900));}
    private static AuthFlowSession copy(AuthFlowSession s,AuthFlowStatus status,Instant updated){return new AuthFlowSession(
            s.authFlowSessionId(),s.scenario(),status,s.csrfTokenHash(),s.activeOtpChallengeId(),s.data(),
            s.createdAt(),updated,s.expiresAt());}

    private static final class Fixture{
        final MemoryCache cache=new MemoryCache();final RedisAuthFlowSessionJsonMapper mapper=new RedisAuthFlowSessionJsonMapper();
        final Ops ops=new Ops(cache);final RedisAuthFlowSessionAdapter adapter;
        Fixture(){var state=new AuthFlowSessionPersistenceCompatibilityState();state.establish();adapter=new RedisAuthFlowSessionAdapter(
                cache,new RedisCacheKeyFactory("ngtpa"),mapper,ops,()->rev("b"),state,Clock.fixed(NOW,ZoneOffset.UTC));}
        AuthFlowSessionSnapshot store(){var snap=new AuthFlowSessionSnapshot(session(),rev("a"));cache.value=mapper.toJson(snap.session(),snap.revision());return snap;}
    }
    private static final class Ops implements RevisionedAuthFlowSessionRedisOperations{
        final MemoryCache cache;long code=1;int replaceCalls;Ops(MemoryCache c){cache=c;}
        public Mono<Boolean> createIfAbsent(String k,String j,Duration t){return Mono.just(false);}
        public Mono<Long> initializeLegacy(String a,String b,String c,String d,String e){return Mono.just(-4L);}
        public Mono<Long> replaceIfCurrent(String k,String expected,String revised){replaceCalls++;if(code==1&&Objects.equals(cache.value,expected))cache.value=revised;return Mono.just(code);}
        public Mono<Long> deleteIfCurrent(String k,String expected){if(code==1&&Objects.equals(cache.value,expected))cache.value=null;return Mono.just(code);}
    }
    private static final class MemoryCache implements CachePort{
        String value;public Mono<Optional<String>> get(String k){return Mono.just(Optional.ofNullable(value));}
        public Mono<Void> set(String k,String v,Duration t){value=v;return Mono.empty();}
        public Mono<Boolean> evict(String k){boolean x=value!=null;value=null;return Mono.just(x);}
    }
}

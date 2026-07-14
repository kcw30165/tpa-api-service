package com.bct.ngtpa.apiservice.adapter.out.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.bct.ngtpa.apiservice.adapter.in.web.session.config.AuthFlowSessionProperties;
import com.bct.ngtpa.apiservice.application.dto.session.authflow.*;
import com.bct.ngtpa.apiservice.application.port.out.AuthFlowSessionPort;
import java.time.Instant;import java.util.*;import org.junit.jupiter.api.Test;import reactor.core.publisher.Mono;import reactor.test.StepVerifier;
class AuthFlowSessionExpiryRefreshAdapterTest {
 @Test void refreshSlidesExpiryFromInteractionTime(){var port=mock(AuthFlowSessionPort.class);var id=new AuthFlowSessionId("afs_safe");var now=Instant.parse("2026-07-14T00:00:00Z");var s=new AuthFlowSession(id,AuthFlowScenario.REGISTRATION,AuthFlowStatus.USERNAME_VERIFIED,"h",null,AuthFlowSessionData.empty(),now.minusSeconds(10),now.minusSeconds(5),now.plusSeconds(5));when(port.load(id)).thenReturn(Mono.just(Optional.of(s)));when(port.update(any())).thenAnswer(i->Mono.just(i.getArgument(0)));var p=new AuthFlowSessionProperties();p.getScenarios().getRegistration().setTtlSeconds(1800);StepVerifier.create(new AuthFlowSessionExpiryRefreshAdapter(port,p).refresh(id,now)).assertNext(x->{assertThat(x.updatedAt()).isEqualTo(now);assertThat(x.expiresAt()).isEqualTo(now.plusSeconds(1800));}).verifyComplete();}
 @Test void terminalSessionIsNotRefreshed(){var port=mock(AuthFlowSessionPort.class);var id=new AuthFlowSessionId("afs_safe");var now=Instant.parse("2026-07-14T00:00:00Z");var s=new AuthFlowSession(id,AuthFlowScenario.REGISTRATION,AuthFlowStatus.COMPLETED,"h",null,AuthFlowSessionData.empty(),now,now,now.plusSeconds(5));when(port.load(id)).thenReturn(Mono.just(Optional.of(s)));StepVerifier.create(new AuthFlowSessionExpiryRefreshAdapter(port,new AuthFlowSessionProperties()).refresh(id,now)).expectError(IllegalStateException.class).verify();verify(port,never()).update(any());}
}

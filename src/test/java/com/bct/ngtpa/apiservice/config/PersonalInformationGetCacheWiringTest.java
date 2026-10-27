package com.bct.ngtpa.apiservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoResult;
import com.bct.ngtpa.apiservice.application.dto.PersonalInformationConfigCacheEntry;
import com.bct.ngtpa.apiservice.application.dto.session.authenticated.AuthenticatedSessionId;
import com.bct.ngtpa.apiservice.application.port.in.GetPersonalInformationUseCase;
import com.bct.ngtpa.apiservice.application.port.out.ApimMemberInfoPort;
import com.bct.ngtpa.apiservice.application.usecase.personalinformation.PersonalInformationConfigCacheService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/** Sub-task 3.3: the use case configuration wires the session cache into the GET use case. */
class PersonalInformationGetCacheWiringTest {

  private static final AuthenticatedSessionId SID =
      new AuthenticatedSessionId("sid_abcdefghijklmnopqrstuvwx");

  @Test
  void useCaseConfigWiresTheCachingUseCase() {
    ApimMemberInfoPort port =
        command ->
            Mono.just(
                new MemberInfoResult(
                    Map.of(
                        "data", Map.of("email", "a@b.test"),
                        "config", Map.of("email", "READONLY"))));
    PersonalInformationConfigCacheService cache = mock(PersonalInformationConfigCacheService.class);
    when(cache.write(any(), any(), any())).thenReturn(Mono.empty());
    GetPersonalInformationUseCase useCase =
        new UseCaseConfig().getPersonalInformationUseCase(port, cache);

    var result =
        useCase
            .executeAndCache(
                new GetPersonalInformationCommand(
                    "en", "JP", "policy-1", "cert-1", "user-1", ActorUserRole.ROLE_USER_MBR),
                Optional.of(SID),
                "acc_abcdefghijklmnopqrstuvwx",
                "req-1")
            .block();

    assertThat(result).isNotNull();
    assertThat(result.config()).containsEntry("email", "READONLY");
    verify(cache).write(eq(SID), any(PersonalInformationConfigCacheEntry.class), eq("req-1"));
  }
}

package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordCompleteRequest;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ForgotPasswordCompletionActorRoleProvenanceTest {
  @Test
  void trustedMapperAssignsMemberRoleServerSide() {
    var mapper = new ForgotPasswordCompleteWebMapper(Duration.ofMinutes(5));
    var command =
        mapper.toCommand(
            new ForgotPasswordCompleteRequest("Password1!", "Password1!"),
            "afs-1",
            "csrf",
            "idem",
            "https://portal.example",
            "https://portal.example/page",
            "en",
            "req-1");
    assertThat(command.actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
  }

  @Test
  void frontendRequestHasNoRoutingAuthority() {
    assertThat(
            Arrays.stream(ForgotPasswordCompleteRequest.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly("password", "confirmPassword");
    assertThat(
            Arrays.stream(ForgotPasswordCompleteRequest.class.getRecordComponents())
                .map(c -> c.getType().getName()))
        .doesNotContain(ActorUserRole.class.getName());
  }
}

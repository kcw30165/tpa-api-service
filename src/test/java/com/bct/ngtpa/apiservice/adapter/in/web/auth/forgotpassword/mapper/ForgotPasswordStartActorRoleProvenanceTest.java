package com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import com.bct.ngtpa.apiservice.adapter.in.web.auth.forgotpassword.dto.ForgotPasswordStartRequest;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ForgotPasswordStartActorRoleProvenanceTest {
    @Test
    void trustedMapperAssignsMemberRoleServerSide() {
        var mapper = new ForgotPasswordStartWebMapper(Duration.ofMinutes(5));
        var command = mapper.toCommand(new ForgotPasswordStartRequest("member01"),
                "idem-1", "req-1", "en", null);
        assertThat(command.actorUserRole()).isEqualTo(ActorUserRole.ROLE_USER_MBR);
    }

    @Test
    void frontendRequestCannotSupplyActorRoleScopeOrEnvironment() {
        assertThat(Arrays.stream(ForgotPasswordStartRequest.class.getRecordComponents())
                .map(RecordComponent::getName)).containsExactly("username");
        assertThat(Arrays.stream(ForgotPasswordStartRequest.class.getRecordComponents())
                .map(component -> component.getType().getName()))
                .doesNotContain(ActorUserRole.class.getName());
    }
}

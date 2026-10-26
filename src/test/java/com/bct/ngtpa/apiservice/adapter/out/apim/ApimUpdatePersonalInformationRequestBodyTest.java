package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bct.ngtpa.apiservice.adapter.out.apim.credential.ApimDatabaseScope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeRequest;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;

class ApimUpdatePersonalInformationRequestBodyTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @ParameterizedTest
  @NullSource
  @ValueSource(booleans = {false, true})
  void sendsExactlySixFieldsWithApplyAllDefaultingToFalseInOneRequest(Boolean applyToAllAccounts)
      throws Exception {
    AtomicReference<ApimExchangeRequest<?, ?>> captured = new AtomicReference<>();
    var executor = capturing(captured);

    new ApimUpdatePersonalInformationAdapter(executor)
        .updateMemberInfo(command(applyToAllAccounts))
        .block();

    verify(executor, times(1)).postEnvelope(any());
    JsonNode node = JSON.readTree(JSON.writeValueAsString(captured.get().request()));
    assertThat(node.size()).isEqualTo(6);
    assertThat(node.has("apply-all")).isTrue();
    assertThat(node.get("apply-all").isBoolean()).isTrue();
    assertThat(node.get("apply-all").booleanValue())
        .isEqualTo(Boolean.TRUE.equals(applyToAllAccounts));
    assertThat(node.get("policy-no").asText()).isEqualTo("POL");
    assertThat(node.get("cert-no").asText()).isEqualTo("CERT");
    assertThat(node.get("login-user-id").asText()).isEqualTo("USER");
    assertThat(node.get("login-user-role").asText()).isEqualTo("role_user_mbr");
    assertThat(node.get("update-fields").get("email").asText()).isEqualTo("a@b.test");
    assertThat(node.has("env")).isFalse();
    assertThat(node.has("accountEnv")).isFalse();
  }

  @Test
  void routingStillCarriesTheAccountEnvironmentThroughTheCredentialContext() {
    AtomicReference<ApimExchangeRequest<?, ?>> captured = new AtomicReference<>();

    new ApimUpdatePersonalInformationAdapter(capturing(captured))
        .updateMemberInfo(command(true))
        .block();

    assertThat(captured.get().databaseScope()).isEqualTo(ApimDatabaseScope.ACCOUNT_ENV);
    assertThat(captured.get().credentialContext().accountEnv()).isEqualTo("DB");
    assertThat(captured.get().credentialContext().actorUserRole())
        .isEqualTo(ActorUserRole.ROLE_USER_MBR);
  }

  private static UpdateMemberInfoCommand command(Boolean applyToAllAccounts) {
    return new UpdateMemberInfoCommand(
        "DB",
        "POL",
        "CERT",
        "USER",
        ActorUserRole.ROLE_USER_MBR,
        applyToAllAccounts,
        Map.of("email", "a@b.test"));
  }

  private static ApimResponseEnvelope<UpdateMemberInfoApimDataItem> success() {
    return ApimResponseEnvelope.<UpdateMemberInfoApimDataItem>builder()
        .response(
            ApimResponseBody.<UpdateMemberInfoApimDataItem>builder()
                .errMessage("")
                .data(
                    List.of(
                        UpdateMemberInfoApimDataItem.builder()
                            .success(true)
                            .policyNo("POL")
                            .certNo("CERT")
                            .env("DB")
                            .refNo("REF")
                            .build()))
                .build())
        .build();
  }

  private static ApimExchangeExecutor capturing(AtomicReference<ApimExchangeRequest<?, ?>> c) {
    var executor = mock(ApimExchangeExecutor.class);
    when(executor.postEnvelope(any()))
        .thenAnswer(
            invocation -> {
              c.set(invocation.getArgument(0));
              return Mono.just(success());
            });
    return executor;
  }
}

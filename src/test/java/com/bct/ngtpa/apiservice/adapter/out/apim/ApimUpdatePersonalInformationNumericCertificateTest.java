package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseBody;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.ApimResponseEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimError;
import com.bct.ngtpa.apiservice.adapter.out.apim.execution.ApimExchangeExecutor;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.UpdateMemberInfoCommand;
import com.bct.ngtpa.apiservice.application.dto.UpdatePersonalInformationResult;
import com.bct.ngtpa.apiservice.application.exception.PersonalInformationUpdateException;
import com.bct.ngtpa.apiservice.application.service.account.AccountIdentifierFormatter;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ApimUpdatePersonalInformationNumericCertificateTest {
  @Test
  void stringCertificateSelectsZeroPaddedAccountAndPreservesReadOnlyError() throws Exception {
    var adapter =
        new ApimUpdatePersonalInformationAdapter(
            Mockito.mock(ApimExchangeExecutor.class), new AccountIdentifierFormatter(11, 4, '0'));
    var item =
        UpdateMemberInfoApimDataItem.builder()
            .success(false)
            .policyNo("00000008802")
            .certNo("38")
            .env("DB")
            .errors(
                List.of(
                    UpdateMemberInfoApimError.builder()
                        .type("FIELD")
                        .fields("corr-addr1")
                        .code("READ_ONLY")
                        .build()))
            .build();
    var envelope =
        ApimResponseEnvelope.<UpdateMemberInfoApimDataItem>builder()
            .response(
                ApimResponseBody.<UpdateMemberInfoApimDataItem>builder()
                    .errMessage("")
                    .data(List.of(item))
                    .build())
            .build();
    var command =
        new UpdateMemberInfoCommand(
            "DB",
            "00000008802",
            "0038",
            "db09",
            ActorUserRole.ROLE_USER_MBR,
            false,
            Map.of("email", "member@example.test"));

    assertThat(invoke(adapter, envelope, command))
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.selected()).isTrue();
              assertThat(result.success()).isFalse();
              assertThat(result.errors())
                  .singleElement()
                  .satisfies(
                      error -> {
                        assertThat(error.type()).isEqualTo("FIELD");
                        assertThat(error.fields()).containsExactly("corr-addr1");
                        assertThat(error.code()).isEqualTo("READ_ONLY");
                      });
            });
  }

  @Test
  void nonNumericPolicyIsRejectedInsteadOfBeingAcceptedByExactComparison() {
    var adapter =
        new ApimUpdatePersonalInformationAdapter(
            Mockito.mock(ApimExchangeExecutor.class), new AccountIdentifierFormatter(11, 4, '0'));
    var item =
        UpdateMemberInfoApimDataItem.builder()
            .success(true)
            .policyNo("POLICY-A")
            .certNo("3")
            .env("DB")
            .build();
    var envelope =
        ApimResponseEnvelope.<UpdateMemberInfoApimDataItem>builder()
            .response(
                ApimResponseBody.<UpdateMemberInfoApimDataItem>builder()
                    .errMessage("")
                    .data(List.of(item))
                    .build())
            .build();
    var command =
        new UpdateMemberInfoCommand(
            "DB",
            "POLICY-A",
            "3",
            "user",
            ActorUserRole.ROLE_USER_MBR,
            false,
            Map.of("email", "x"));
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> invoke(adapter, envelope, command))
        .isInstanceOf(java.lang.reflect.InvocationTargetException.class)
        .hasCauseInstanceOf(PersonalInformationUpdateException.class);
  }

  @SuppressWarnings("unchecked")
  private static List<UpdatePersonalInformationResult> invoke(
      ApimUpdatePersonalInformationAdapter adapter,
      ApimResponseEnvelope<UpdateMemberInfoApimDataItem> envelope,
      UpdateMemberInfoCommand command)
      throws Exception {
    Method method =
        ApimUpdatePersonalInformationAdapter.class.getDeclaredMethod(
            "toResults", ApimResponseEnvelope.class, UpdateMemberInfoCommand.class);
    method.setAccessible(true);
    return (List<UpdatePersonalInformationResult>) method.invoke(adapter, envelope, command);
  }
}

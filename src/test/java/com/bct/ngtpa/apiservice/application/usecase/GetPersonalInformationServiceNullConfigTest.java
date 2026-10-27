package com.bct.ngtpa.apiservice.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;
import com.bct.ngtpa.apiservice.application.dto.GetPersonalInformationCommand;
import com.bct.ngtpa.apiservice.application.dto.MemberInfoResult;
import com.bct.ngtpa.apiservice.application.port.out.ApimMemberInfoPort;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/** A null downstream config value is a blank access value (D-32a), not the text "null". */
class GetPersonalInformationServiceNullConfigTest {

  private static GetPersonalInformationCommand command() {
    return new GetPersonalInformationCommand(
        "en", "JP", "policy-1", "cert-1", "user-1", ActorUserRole.ROLE_USER_MBR);
  }

  private static ApimMemberInfoPort portReturning(Map<String, Object> config) {
    return fetchCommand ->
        Mono.just(
            new MemberInfoResult(Map.of("data", Map.of("email", "a@b.test"), "config", config)));
  }

  @Test
  void nullConfigValueBecomesBlank() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("addr1", null);
    config.put("email", "READONLY");

    var result =
        new GetPersonalInformationService(portReturning(config)).execute(command()).block();

    assertThat(result).isNotNull();
    assertThat(result.config()).containsEntry("addr1", "").containsEntry("email", "READONLY");
  }

  @Test
  void nonNullConfigValuesAreUnchanged() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("addr1", "EDITABLE_COM");
    config.put("email", "READONLY");

    var result =
        new GetPersonalInformationService(portReturning(config)).execute(command()).block();

    assertThat(result).isNotNull();
    assertThat(result.config())
        .containsEntry("addr1", "EDITABLE_COM")
        .containsEntry("email", "READONLY");
  }
}

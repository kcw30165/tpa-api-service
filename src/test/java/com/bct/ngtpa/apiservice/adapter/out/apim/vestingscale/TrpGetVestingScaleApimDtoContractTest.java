package com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.out.apim.vestingscale.dto.TrpGetVestingScaleApimRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TrpGetVestingScaleApimDtoContractTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void serializesExactlyFourFieldsAndUsesTheMemberRoleWireValue() throws Exception {
    var request =
        new TrpGetVestingScaleApimRequest("actor-123", "role_user_mbr", "policy-456", "cert-789");

    var json = objectMapper.readTree(objectMapper.writeValueAsString(request));

    assertThat(json.size()).isEqualTo(4);
    assertThat(json.get("login-user-id").textValue()).isEqualTo("actor-123");
    assertThat(json.get("login-user-role").textValue()).isEqualTo("role_user_mbr");
    assertThat(json.get("policy-no").textValue()).isEqualTo("policy-456");
    assertThat(json.get("cert-no").textValue()).isEqualTo("cert-789");
  }
}

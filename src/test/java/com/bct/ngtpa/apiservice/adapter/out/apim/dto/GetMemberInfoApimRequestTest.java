package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class GetMemberInfoApimRequestTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void serializesExactlyTheFourTrpGetMemberInfoRequestFields() throws Exception {
    GetMemberInfoApimRequest request =
        GetMemberInfoApimRequest.builder()
            .policyNo("POL-1")
            .certNo("CERT-1")
            .loginUserId("USER-1")
            .loginUserRole("role_user_mbr")
            .build();

    JsonNode node = JSON.readTree(JSON.writeValueAsString(request));

    assertThat(node.size()).isEqualTo(4);
    assertThat(node.get("policy-no").asText()).isEqualTo("POL-1");
    assertThat(node.get("cert-no").asText()).isEqualTo("CERT-1");
    assertThat(node.get("login-user-id").asText()).isEqualTo("USER-1");
    assertThat(node.get("login-user-role").asText()).isEqualTo("role_user_mbr");
    assertThat(node.has("env")).isFalse();
    assertThat(node.has("accountEnv")).isFalse();
  }

  @Test
  void declaresNoEnvironmentField() {
    assertThat(
            Arrays.stream(GetMemberInfoApimRequest.class.getDeclaredFields()).map(Field::getName))
        .isNotEmpty()
        .doesNotContain("accountEnv", "env");
  }
}

package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class UpdateMemberInfoRequestTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void serializesExactlyTheFiveRequestFields() throws Exception {
    String source =
        "{\"policy-no\":\"POL-1\",\"cert-no\":\"CERT-1\","
            + "\"update-fields\":{\"email\":\"a@b.test\"},"
            + "\"login-user-id\":\"USER-1\",\"login-user-role\":\"role_user_mbr\"}";

    UpdateMemberInfoRequest request = JSON.readValue(source, UpdateMemberInfoRequest.class);
    JsonNode node = JSON.readTree(JSON.writeValueAsString(request));

    assertThat(node.size()).isEqualTo(5);
    assertThat(node.get("policy-no").asText()).isEqualTo("POL-1");
    assertThat(node.get("cert-no").asText()).isEqualTo("CERT-1");
    assertThat(node.get("update-fields").get("email").asText()).isEqualTo("a@b.test");
    assertThat(node.get("login-user-id").asText()).isEqualTo("USER-1");
    assertThat(node.get("login-user-role").asText()).isEqualTo("role_user_mbr");
    assertThat(node.has("env")).isFalse();
  }

  @Test
  void declaresNoEnvironmentComponent() {
    assertThat(
            Arrays.stream(UpdateMemberInfoRequest.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly("policyNo", "certNo", "updateFields", "loginUserId", "loginUserRole");
  }
}

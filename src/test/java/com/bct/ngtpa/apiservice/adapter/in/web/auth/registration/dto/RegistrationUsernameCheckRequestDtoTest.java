package com.bct.ngtpa.apiservice.adapter.in.web.auth.registration.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RegistrationUsernameCheckRequestDtoTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestDtoSerializesExpectedShape() throws Exception {
        String json = """
                {
                  "selectedTrust": {
                    "trustCode": "TL",
                    "accountEnv": "ENV1"
                  },
                  "username": "abc1@"
                }
                """;

        RegistrationUsernameCheckRequest request = objectMapper.readValue(
                json,
                RegistrationUsernameCheckRequest.class);
        JsonNode tree = objectMapper.valueToTree(request);

        assertThat(request.selectedTrust().trustCode()).isEqualTo("TL");
        assertThat(request.selectedTrust().accountEnv()).isEqualTo("ENV1");
        assertThat(request.username()).isEqualTo("abc1@");
        assertThat(tree.has("policyNo")).isFalse();
        assertThat(tree.has("certNo")).isFalse();
        assertThat(tree.get("selectedTrust").has("policyNo")).isFalse();
        assertThat(tree.get("selectedTrust").has("certNo")).isFalse();
    }

    @Test
    void requestDtoRedactsUsernameInToString() {
        var request = new RegistrationUsernameCheckRequest(
                new RegistrationSelectedTrustRequest("TL", "ENV1"),
                "abc1@");

        assertThat(request.toString()).doesNotContain("abc1@");
        assertThat(request.toString()).contains("username=<redacted>");
    }

    @Test
    void selectedTrustRequestContainsOnlyTrustCodeAndAccountEnv() {
        assertThat(componentNames(RegistrationSelectedTrustRequest.class))
                .containsExactly("trustCode", "accountEnv")
                .doesNotContain("policyNo", "certNo");
    }

    private static java.util.List<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}

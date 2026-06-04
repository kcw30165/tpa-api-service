package com.bct.ngtpa.apiservice.adapter.out.apim.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UpdateMemberInfoApimRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void isJacksonTransformableForApimPayloadCryptoService() throws Exception {
        var source = UpdateMemberInfoApimRequest.builder()
                .policyNo("POL-001")
                .certNo("CERT-001")
                .accountEnv("JP")
                .userId("actor-user")
                .updateFields(Map.of("mobile-number", "98765432"))
                .build();

        var tree = objectMapper.valueToTree(source);
        var transformed = objectMapper.treeToValue(tree, UpdateMemberInfoApimRequest.class);

        assertEquals("POL-001", transformed.getPolicyNo());
        assertEquals("CERT-001", transformed.getCertNo());
        assertEquals("JP", transformed.getAccountEnv());
        assertEquals("actor-user", transformed.getUserId());
        assertEquals("98765432", transformed.getUpdateFields().get("mobile-number"));
    }
}

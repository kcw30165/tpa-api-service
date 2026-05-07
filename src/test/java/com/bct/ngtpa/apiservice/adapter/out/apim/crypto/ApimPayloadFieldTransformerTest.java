package com.bct.ngtpa.apiservice.adapter.out.apim.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApimPayloadFieldTransformerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ApimPayloadFieldTransformer transformer = new ApimPayloadFieldTransformer();

    @Test
    void transformsConfiguredTextFieldsAcrossObjectsAndArrays() throws Exception {
        JsonNode source = OBJECT_MAPPER.readTree("""
                {
                  "secret": "root",
                  "plain": "keep",
                  "nested": {
                    "secret": "nested",
                    "count": 3
                  },
                  "items": [
                    {"secret": "first"},
                    {"secret": 4},
                    null
                  ]
                }
                """);

        JsonNode transformed = transformer.transformFields(source, Set.of("secret"), value -> "signed-" + value);

        assertEquals("root", source.get("secret").asText());
        assertEquals("signed-root", transformed.get("secret").asText());
        assertEquals("keep", transformed.get("plain").asText());
        assertEquals("signed-nested", transformed.get("nested").get("secret").asText());
        assertEquals(3, transformed.get("nested").get("count").asInt());
        assertEquals("signed-first", transformed.get("items").get(0).get("secret").asText());
        assertEquals(4, transformed.get("items").get(1).get("secret").asInt());
        assertSame(NullNode.getInstance(), transformed.get("items").get(2));
    }

    @Test
    void preservesNullAndScalarRoots() {
        JsonNode transformedNull = transformer.transformFields(NullNode.getInstance(), Set.of("secret"), String::toUpperCase);
        JsonNode transformedScalar = transformer.transformFields(TextNode.valueOf("value"), Set.of("secret"), String::toUpperCase);

        assertSame(NullNode.getInstance(), transformedNull);
        assertEquals("value", transformedScalar.asText());
    }
}
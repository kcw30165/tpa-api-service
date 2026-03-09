package com.bct.ngtpa.apiservice.util.apim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

@Component
public class JsonFieldCryptoUtil {
    public JsonNode transformFields(
            JsonNode sourceNode,
            Set<String> targetFields,
            UnaryOperator<String> transformFn) {
        JsonNode resultNode = sourceNode.deepCopy();
        transform(resultNode, targetFields, transformFn);
        return resultNode;
    }

    private void transform(JsonNode currentNode, Set<String> targetFields, UnaryOperator<String> transformFn) {
        if (currentNode == null || currentNode.isNull()) {
            return;
        }

        if (currentNode.isArray()) {
            ArrayNode arrayNode = (ArrayNode) currentNode;
            for (JsonNode node : arrayNode) {
                transform(node, targetFields, transformFn);
            }
            return;
        }

        if (!currentNode.isObject()) {
            return;
        }

        ObjectNode objectNode = (ObjectNode) currentNode;
        for (Map.Entry<String, JsonNode> field : objectNode.properties()) {
            String fieldName = field.getKey();
            JsonNode fieldValue = field.getValue();

            if (targetFields.contains(fieldName) && fieldValue.isTextual()) {
                objectNode.put(fieldName, transformFn.apply(fieldValue.asText()));
            } else {
                transform(fieldValue, targetFields, transformFn);
            }
        }
    }
}

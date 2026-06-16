package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PersonalInformationDisplayResolutionGuardTest {

    private static final Path MAPPER_SOURCE = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/mapper/PersonalInformationWebMapper.java");
    private static final Path CONTROLLER_SOURCE = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/PersonalInformationController.java");

    @Test
    void controllerResolvesPortalContextAndPassesAccountDimensionsToMapper() throws IOException {
        String controller = Files.readString(CONTROLLER_SOURCE);

        assertTrue(controller.contains("import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextProvider;"));
        assertTrue(controller.contains("private final CurrentPortalAccessContextProvider currentPortalAccessContextProvider;"));
        assertTrue(controller.contains("currentPortalAccessContextProvider.current()"));
        assertTrue(controller.contains("accountEnv(portalAccessContext)"));
        assertTrue(controller.contains("trustCode(portalAccessContext)"));
        assertTrue(controller.contains("schemeType(portalAccessContext)"));
        assertTrue(controller.contains("personalInformationWebMapper.toFormPageResponse("));
    }

    @Test
    void mapperKeepsContextAwareAndBackwardCompatibleEntryPoints() throws IOException {
        String mapper = Files.readString(MAPPER_SOURCE);

        assertTrue(mapper.contains("String accountEnv"));
        assertTrue(mapper.contains("String trustCode"));
        assertTrue(mapper.contains("String schemeType"));
        assertTrue(mapper.contains("return toFormPageResponse(result, language, null, null, null);"));
        assertTrue(mapper.contains("return toResponse(apimData, apimConfig, language, null, null, null);"));
        assertTrue(mapper.contains(
                "return toResponse(apimData, apimConfig, apimConfigItems, language, null, null, null);"));
        assertTrue(mapper.contains("return toFormPageResponse(apimData, apimConfig, language, null, null, null);"));
        assertTrue(mapper.contains(
                "return toFormPageResponse(apimData, apimConfig, apimConfigItems, language, null, null, null);"));
    }

    @Test
    void mapperUsesDisplayCodesForNestedDisplayText() throws IOException {
        String mapper = Files.readString(MAPPER_SOURCE);

        assertTrue(mapper.contains("sectionSchema.getTitleCode()"));
        assertTrue(mapper.contains("fieldSchema.getLabelCode()"));
        assertTrue(mapper.contains("fieldSchema.getPlaceholderCode()"));
        assertTrue(mapper.contains("actionSchema.getLabelCode()"));
        assertTrue(mapper.contains("rule.getMessageCode()"));
        assertTrue(mapper.contains("resolvePageDisplayText("));
        assertTrue(mapper.contains("pageSchema.getDisplay()"));
    }

    @Test
    void validationMessageResolutionPreservesRuleObjectAndRestoresInlineMessageFallback() throws IOException {
        String mapper = Files.readString(MAPPER_SOURCE);

        assertTrue(mapper.contains("java.util.Map<String, String> originalMessage = rule.getMessage();"));
        assertTrue(mapper.contains("rule.setMessage(Collections.singletonMap(language, resolvedMessage));"));
        assertTrue(mapper.contains("rule.setMessage(originalMessage);"));
        assertFalse(mapper.contains("new ValidationRuleProperties()"),
                "Validation rules must not be manually copied because generic fields such as value must be preserved.");
    }

    @Test
    void temporaryNullContextBridgeMustNotReturn() throws IOException {
        String mapper = Files.readString(MAPPER_SOURCE);

        assertFalse(mapper.contains("DEFAULT_ACCOUNT_ENV"));
        assertFalse(mapper.contains("DEFAULT_TRUST_CODE"));
        assertFalse(mapper.contains("DEFAULT_SCHEME_TYPE"));
        assertFalse(mapper.contains("private final String accountEnv ="));
        assertFalse(mapper.contains("private final String trustCode ="));
        assertFalse(mapper.contains("private final String schemeType ="));
    }
}

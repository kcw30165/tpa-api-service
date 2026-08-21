package com.bct.ngtpa.apiservice.adapter.in.web.mapper;

import static com.bct.ngtpa.apiservice.architecture.SourceTextAssertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PersonalInformationDisplayResolutionGuardTest {

  private static final Path MAPPER_SOURCE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/mapper/PersonalInformationWebMapper.java");
  private static final Path CONTROLLER_SOURCE =
      Path.of(
          "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/personalinformation/PersonalInformationController.java");

  @Test
  void controllerResolvesPortalContextAndPassesAccountDimensionsToMapper() throws IOException {
    String controller = Files.readString(CONTROLLER_SOURCE);

    assertTrue(
        containsIgnoringWhitespace(
            controller,
            "import com.bct.ngtpa.apiservice.application.port.out.CurrentPortalAccessContextResolver;"));
    assertTrue(
        containsIgnoringWhitespace(
            controller,
            "private final CurrentPortalAccessContextResolver currentPortalAccessContextResolver;"));
    assertTrue(
        containsIgnoringWhitespace(controller, "currentPortalAccessContextResolver.current()"));
    assertTrue(containsIgnoringWhitespace(controller, "accountEnv(portalAccessContext)"));
    assertTrue(containsIgnoringWhitespace(controller, "trustCode(portalAccessContext)"));
    assertTrue(containsIgnoringWhitespace(controller, "schemeType(portalAccessContext)"));
    assertTrue(
        containsIgnoringWhitespace(controller, "personalInformationWebMapper.toFormPageResponse("));
  }

  @Test
  void mapperKeepsContextAwareAndBackwardCompatibleEntryPoints() throws IOException {
    String mapper = Files.readString(MAPPER_SOURCE);

    assertTrue(containsIgnoringWhitespace(mapper, "String accountEnv"));
    assertTrue(containsIgnoringWhitespace(mapper, "String trustCode"));
    assertTrue(containsIgnoringWhitespace(mapper, "String schemeType"));
    assertTrue(
        containsIgnoringWhitespace(
            mapper, "return toFormPageResponse(result, language, null, null, null);"));
    assertTrue(
        containsIgnoringWhitespace(
            mapper, "return toResponse(apimData, apimConfig, language, null, null, null);"));
    assertTrue(
        containsIgnoringWhitespace(
            mapper,
            "return toResponse(apimData, apimConfig, apimConfigItems, language, null, null, null);"));
    assertTrue(
        containsIgnoringWhitespace(
            mapper,
            "return toFormPageResponse(apimData, apimConfig, language, null, null, null);"));
    assertTrue(
        containsIgnoringWhitespace(
            mapper,
            "return toFormPageResponse(apimData, apimConfig, apimConfigItems, language, null, null, null);"));
  }

  @Test
  void mapperUsesDisplayCodesForNestedDisplayText() throws IOException {
    String mapper = Files.readString(MAPPER_SOURCE);

    assertTrue(containsIgnoringWhitespace(mapper, "sectionSchema.getTitleCode()"));
    assertTrue(containsIgnoringWhitespace(mapper, "fieldSchema.getLabelCode()"));
    assertTrue(containsIgnoringWhitespace(mapper, "fieldSchema.getPlaceholderCode()"));
    assertTrue(containsIgnoringWhitespace(mapper, "actionSchema.getLabelCode()"));
    assertTrue(containsIgnoringWhitespace(mapper, "rule.getMessageCode()"));
    assertTrue(containsIgnoringWhitespace(mapper, "resolvePageDisplayText("));
    assertTrue(containsIgnoringWhitespace(mapper, "pageSchema.getDisplay()"));
  }

  @Test
  void validationMessageResolutionPreservesRuleObjectAndRestoresInlineMessageFallback()
      throws IOException {
    String mapper = Files.readString(MAPPER_SOURCE);

    assertTrue(
        containsIgnoringWhitespace(
            mapper, "java.util.Map<String, String> originalMessage = rule.getMessage();"));
    assertTrue(
        containsIgnoringWhitespace(
            mapper, "rule.setMessage(Collections.singletonMap(language, resolvedMessage));"));
    assertTrue(containsIgnoringWhitespace(mapper, "rule.setMessage(originalMessage);"));
    assertFalse(
        containsIgnoringWhitespace(mapper, "new ValidationRuleProperties()"),
        "Validation rules must not be manually copied because generic fields such as value must be preserved.");
  }

  @Test
  void temporaryNullContextBridgeMustNotReturn() throws IOException {
    String mapper = Files.readString(MAPPER_SOURCE);

    assertFalse(containsIgnoringWhitespace(mapper, "DEFAULT_ACCOUNT_ENV"));
    assertFalse(containsIgnoringWhitespace(mapper, "DEFAULT_TRUST_CODE"));
    assertFalse(containsIgnoringWhitespace(mapper, "DEFAULT_SCHEME_TYPE"));
    assertFalse(containsIgnoringWhitespace(mapper, "private final String accountEnv ="));
    assertFalse(containsIgnoringWhitespace(mapper, "private final String trustCode ="));
    assertFalse(containsIgnoringWhitespace(mapper, "private final String schemeType ="));
  }
}

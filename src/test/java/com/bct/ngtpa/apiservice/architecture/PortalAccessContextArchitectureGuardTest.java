package com.bct.ngtpa.apiservice.architecture;

import static com.bct.ngtpa.apiservice.architecture.SourceTextAssertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PortalAccessContextArchitectureGuardTest {

  private static final Path MAIN_JAVA = Path.of("src/main/java");
  private static final Path API_EXCEPTION_HANDLER =
      MAIN_JAVA.resolve("com/bct/ngtpa/apiservice/adapter/in/web/advice/ApiExceptionHandler.java");
  private static final Path PUBLIC_MESSAGE_RESOLVER =
      MAIN_JAVA.resolve("com/bct/ngtpa/apiservice/adapter/in/web/error/PublicMessageResolver.java");
  private static final Path PERSONAL_INFORMATION_CONTROLLER =
      MAIN_JAVA.resolve(
          "com/bct/ngtpa/apiservice/adapter/in/web/personalinformation/PersonalInformationController.java");
  private static final Path REQUEST_LOGGING_FILTER =
      MAIN_JAVA.resolve(
          "com/bct/ngtpa/apiservice/adapter/in/web/filter/RequestLoggingWebFilter.java");
  private static final Path PORTAL_ACCESS_CONTEXT_FILTER =
      MAIN_JAVA.resolve(
          "com/bct/ngtpa/apiservice/adapter/in/web/filter/PortalAccessContextWebFilter.java");
  private static final Path REQUEST_HEADER_CONTEXT_FILTER =
      MAIN_JAVA.resolve(
          "com/bct/ngtpa/apiservice/adapter/in/web/filter/RequestHeaderContextWebFilter.java");
  private static final Path ACTOR_CONTEXT =
      MAIN_JAVA.resolve("com/bct/ngtpa/apiservice/application/dto/ActorContext.java");

  @Test
  void apiExceptionHandlerUsesCurrentPortalAccessContextOnly() throws Exception {
    String source = Files.readString(API_EXCEPTION_HANDLER);

    assertFalse(
        containsIgnoringWhitespace(source, "PortalAccessContextPort"),
        "ApiExceptionHandler must not resolve PortalAccessContext by Account-Ref through PortalAccessContextPort");
    assertFalse(
        containsIgnoringWhitespace(source, "resolveAccountRef(ServerWebExchange"),
        "ApiExceptionHandler must not resolve Account-Ref directly");
    assertFalse(
        containsIgnoringWhitespace(source, "requestParam(exchange, \"env\")"),
        "ApiExceptionHandler must not read env from request params");
    assertFalse(
        containsIgnoringWhitespace(source, "requestParam(exchange, \"trustCode\")"),
        "ApiExceptionHandler must not read trustCode from request params");
    assertFalse(
        containsIgnoringWhitespace(source, "requestParam(exchange, \"schemeType\")"),
        "ApiExceptionHandler must not read schemeType from request params");
    assertTrue(
        containsIgnoringWhitespace(source, "PublicMessageResolver"),
        "ApiExceptionHandler should delegate portal-context-aware message resolution to PublicMessageResolver");

    // Sub-task 1.8.3: the direct PortalAccessContextKeys.ATTRIBUTE_KEY read moved
    // into PublicMessageResolver (sub-task 1.8.1); ApiExceptionHandler now reaches
    // the current request's PortalAccessContext only via that delegate.
    String messageResolverSource = Files.readString(PUBLIC_MESSAGE_RESOLVER);
    assertTrue(
        containsIgnoringWhitespace(messageResolverSource, "PortalAccessContextKeys.ATTRIBUTE_KEY"),
        "PublicMessageResolver (used by ApiExceptionHandler) should read the PortalAccessContext populated for the current request");
  }

  @Test
  void personalInformationGetControllerUsesCurrentPortalAccessContextOnly() throws Exception {
    String source = Files.readString(PERSONAL_INFORMATION_CONTROLLER);

    assertFalse(
        containsIgnoringWhitespace(source, "PortalAccessContextPort"),
        "PersonalInformationController must not directly call PortalAccessContextPort");
    assertFalse(
        containsIgnoringWhitespace(source, "resolveRequiredAccountRef(ContextView"),
        "PersonalInformationController must not manually resolve Account-Ref from RequestHeaderContext");
    assertTrue(
        containsIgnoringWhitespace(source, "CurrentPortalAccessContextResolver"),
        "PersonalInformationController should use the current request context resolver");
    assertTrue(
        containsIgnoringWhitespace(source, "currentPortalAccessContextResolver.current()"),
        "PersonalInformationController should use the already-populated current PortalAccessContext");
    assertTrue(
        containsIgnoringWhitespace(source, "requireAccountRef(portalAccessContext)"),
        "PersonalInformationController should validate accountRef from PortalAccessContext.account()");
    assertTrue(
        containsIgnoringWhitespace(source, "new GetPersonalInformationCommand(language,"),
        "PersonalInformationController should copy trusted context into the GET command");
    assertTrue(containsIgnoringWhitespace(source, "portalAccessContext.account().policyNo()"));
    assertTrue(containsIgnoringWhitespace(source, "portalAccessContext.actor().actorUserId()"));
    assertFalse(
        containsIgnoringWhitespace(source, "new GetPersonalInformationCommand(accountRef"),
        "PersonalInformationController must not pass accountRef into GET command");
  }

  @Test
  void webFilterOrderingKeepsHeaderContextBeforePortalContext() throws Exception {
    String requestLogging = Files.readString(REQUEST_LOGGING_FILTER);
    String portalContext = Files.readString(PORTAL_ACCESS_CONTEXT_FILTER);
    String requestHeader = Files.readString(REQUEST_HEADER_CONTEXT_FILTER);

    assertTrue(
        containsIgnoringWhitespace(requestLogging, "@Order(Ordered.HIGHEST_PRECEDENCE)"),
        "RequestLoggingWebFilter should run first because it creates RequestHeaderContext");
    assertTrue(
        containsIgnoringWhitespace(requestLogging, "RequestHeaderContextKeys.ATTRIBUTE_KEY"),
        "RequestLoggingWebFilter should populate RequestHeaderContext exchange attribute");
    assertTrue(
        containsIgnoringWhitespace(portalContext, "@Order(Ordered.HIGHEST_PRECEDENCE + 1)"),
        "PortalAccessContextWebFilter should run immediately after RequestLoggingWebFilter");
    assertTrue(
        containsIgnoringWhitespace(portalContext, "RequestHeaderContextKeys.ATTRIBUTE_KEY"),
        "PortalAccessContextWebFilter should prefer RequestHeaderContext created by RequestLoggingWebFilter");
    assertTrue(
        containsIgnoringWhitespace(
            requestHeader, "requestLoggingWebFilter.filter(exchange, chain)"),
        "RequestHeaderContextWebFilter remains a compatibility delegating wrapper around RequestLoggingWebFilter");
  }

  @Test
  void portalAccessContextIsNotStoredInApplicationGlobalState() throws Exception {
    List<Path> files = javaFiles(MAIN_JAVA);
    for (Path file : files) {
      String source = Files.readString(file);
      if (!containsIgnoringWhitespace(source, "PortalAccessContext")) {
        continue;
      }
      assertFalse(
          containsIgnoringWhitespace(source, "ThreadLocal<PortalAccessContext"),
          file + " must not store PortalAccessContext in ThreadLocal");
      assertFalse(
          containsIgnoringWhitespace(source, "static PortalAccessContext"),
          file + " must not store PortalAccessContext in static global state");
      assertFalse(
          containsIgnoringWhitespace(source, "Map<String, PortalAccessContext>"),
          file + " must not keep an application-global session/context map");
    }
  }

  @Test
  void actorContextDoesNotReintroduceActorUserType() throws Exception {
    String source = Files.readString(ACTOR_CONTEXT);

    assertFalse(
        containsIgnoringWhitespace(source, "actorUserType"),
        "actorUserType has been removed and must not be reintroduced into ActorContext");
  }

  @Test
  void temporarySessionYamlShapeRemainsDocumentedInLocalConfig() throws Exception {
    String source = Files.readString(Path.of("src/main/resources/application-local.yml"));

    assertFalse(
        containsIgnoringWhitespace(source, "temporary-portal-access-context:"),
        "local config should not keep the temporary portal access context block");
    assertFalse(
        containsIgnoringWhitespace(source, "default-session-id:"),
        "temporary config block must be removed");
    assertFalse(
        containsIgnoringWhitespace(source, "sessions:"),
        "temporary sessions block must be removed");
    assertFalse(
        containsIgnoringWhitespace(source, "accounts:"),
        "temporary accounts block must be removed");
    assertFalse(
        containsIgnoringWhitespace(source, "temporary-portal-access-context:\nprofiles:"),
        "local config should not use the old accountRef-only profiles root");
  }

  private static List<Path> javaFiles(Path root) throws IOException {
    try (Stream<Path> stream = Files.walk(root)) {
      return stream.filter(path -> path.toString().endsWith(".java")).toList();
    }
  }
}

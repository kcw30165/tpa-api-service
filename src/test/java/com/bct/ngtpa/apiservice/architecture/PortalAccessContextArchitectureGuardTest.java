package com.bct.ngtpa.apiservice.architecture;

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
    private static final Path API_EXCEPTION_HANDLER = MAIN_JAVA.resolve(
            "com/bct/ngtpa/apiservice/adapter/in/web/controller/ApiExceptionHandler.java");
    private static final Path PERSONAL_INFORMATION_CONTROLLER = MAIN_JAVA.resolve(
            "com/bct/ngtpa/apiservice/adapter/in/web/controller/PersonalInformationController.java");
    private static final Path REQUEST_LOGGING_FILTER = MAIN_JAVA.resolve(
            "com/bct/ngtpa/apiservice/adapter/in/web/filter/RequestLoggingWebFilter.java");
    private static final Path PORTAL_ACCESS_CONTEXT_FILTER = MAIN_JAVA.resolve(
            "com/bct/ngtpa/apiservice/adapter/in/web/filter/PortalAccessContextWebFilter.java");
    private static final Path REQUEST_HEADER_CONTEXT_FILTER = MAIN_JAVA.resolve(
            "com/bct/ngtpa/apiservice/adapter/in/web/filter/RequestHeaderContextWebFilter.java");
    private static final Path ACTOR_CONTEXT = MAIN_JAVA.resolve(
            "com/bct/ngtpa/apiservice/application/dto/ActorContext.java");

    @Test
    void apiExceptionHandlerUsesCurrentPortalAccessContextOnly() throws Exception {
        String source = Files.readString(API_EXCEPTION_HANDLER);

        assertFalse(source.contains("PortalAccessContextPort"),
                "ApiExceptionHandler must not resolve PortalAccessContext by Account-Ref through PortalAccessContextPort");
        assertFalse(source.contains("resolveAccountRef(ServerWebExchange"),
                "ApiExceptionHandler must not resolve Account-Ref directly");
        assertFalse(source.contains("requestParam(exchange, \\\"env\\\")"),
                "ApiExceptionHandler must not read env from request params");
        assertFalse(source.contains("requestParam(exchange, \\\"trustCode\\\")"),
                "ApiExceptionHandler must not read trustCode from request params");
        assertFalse(source.contains("requestParam(exchange, \\\"schemeType\\\")"),
                "ApiExceptionHandler must not read schemeType from request params");
        assertTrue(source.contains("PortalAccessContextKeys.ATTRIBUTE_KEY"),
                "ApiExceptionHandler should read the PortalAccessContext populated for the current request");
    }

    @Test
    void personalInformationGetControllerUsesCurrentPortalAccessContextOnly() throws Exception {
        String source = Files.readString(PERSONAL_INFORMATION_CONTROLLER);

        assertFalse(source.contains("PortalAccessContextPort"),
                "PersonalInformationController must not directly call PortalAccessContextPort");
        assertFalse(source.contains("resolveRequiredAccountRef(ContextView"),
                "PersonalInformationController must not manually resolve Account-Ref from RequestHeaderContext");
        assertTrue(source.contains("PortalAccessContextResolver"),
                "PersonalInformationController should use the current request context resolver");
        assertTrue(source.contains("portalAccessContextResolver.current()"),
                "PersonalInformationController should use the already-populated current PortalAccessContext");
        assertTrue(source.contains("requireAccountRef(portalAccessContext)"),
                "PersonalInformationController should validate accountRef from PortalAccessContext.account()");
        assertTrue(source.contains("new GetPersonalInformationCommand(language)"),
                "PersonalInformationController should keep GET command language-only");
        assertFalse(source.contains("new GetPersonalInformationCommand(accountRef"),
                "PersonalInformationController must not pass accountRef into GET command");
    }

    @Test
    void webFilterOrderingKeepsHeaderContextBeforePortalContext() throws Exception {
        String requestLogging = Files.readString(REQUEST_LOGGING_FILTER);
        String portalContext = Files.readString(PORTAL_ACCESS_CONTEXT_FILTER);
        String requestHeader = Files.readString(REQUEST_HEADER_CONTEXT_FILTER);

        assertTrue(requestLogging.contains("@Order(Ordered.HIGHEST_PRECEDENCE)"),
                "RequestLoggingWebFilter should run first because it creates RequestHeaderContext");
        assertTrue(requestLogging.contains("RequestHeaderContextKeys.ATTRIBUTE_KEY"),
                "RequestLoggingWebFilter should populate RequestHeaderContext exchange attribute");
        assertTrue(portalContext.contains("@Order(Ordered.HIGHEST_PRECEDENCE + 1)"),
                "PortalAccessContextWebFilter should run immediately after RequestLoggingWebFilter");
        assertTrue(portalContext.contains("RequestHeaderContextKeys.ATTRIBUTE_KEY"),
                "PortalAccessContextWebFilter should prefer RequestHeaderContext created by RequestLoggingWebFilter");
        assertTrue(requestHeader.contains("requestLoggingWebFilter.filter(exchange, chain)"),
                "RequestHeaderContextWebFilter remains a compatibility delegating wrapper around RequestLoggingWebFilter");
    }

    @Test
    void portalAccessContextIsNotStoredInApplicationGlobalState() throws Exception {
        List<Path> files = javaFiles(MAIN_JAVA);
        for (Path file : files) {
            String source = Files.readString(file);
            if (!source.contains("PortalAccessContext")) {
                continue;
            }
            assertFalse(source.contains("ThreadLocal<PortalAccessContext"),
                    file + " must not store PortalAccessContext in ThreadLocal");
            assertFalse(source.contains("static PortalAccessContext"),
                    file + " must not store PortalAccessContext in static global state");
            assertFalse(source.contains("Map<String, PortalAccessContext>"),
                    file + " must not keep an application-global session/context map");
        }
    }

    @Test
    void actorContextDoesNotReintroduceActorUserType() throws Exception {
        String source = Files.readString(ACTOR_CONTEXT);

        assertFalse(source.contains("actorUserType"),
                "actorUserType has been removed and must not be reintroduced into ActorContext");
    }

    @Test
    void temporarySessionYamlShapeRemainsDocumentedInLocalConfig() throws Exception {
        String source = Files.readString(Path.of("src/main/resources/application-local.yml"));

        assertTrue(source.contains("temporary-portal-access-context:"),
                "local config should keep the temporary portal access context block");
        assertTrue(source.contains("default-session-id:"),
                "temporary config should simulate Redis with a session-id layer");
        assertTrue(source.contains("sessions:"),
                "temporary config should use sessions above accounts");
        assertTrue(source.contains("accounts:"),
                "temporary config should keep Account-Ref entries under the session");
        assertFalse(source.contains("temporary-portal-access-context:\nprofiles:"),
                "local config should not use the old accountRef-only profiles root");
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }
}

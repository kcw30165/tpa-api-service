package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PortalAccessContextResponsibilityBoundaryGuardTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path WEB_FILTER = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/filter/PortalAccessContextWebFilter.java");

    @Test
    void portalAccessContextWebFilterOwnsExternalContextResolution() throws Exception {
        String source = Files.readString(WEB_FILTER);

        assertTrue(source.contains("PortalAccessContextPort"),
                "PortalAccessContextWebFilter should use PortalAccessContextPort to resolve external context");
        assertTrue(source.contains("resolvePortalAccessContext"),
                "PortalAccessContextWebFilter should call the external resolution port once per selected-account request");
        assertTrue(source.contains("PortalAccessContextKeys.CONTEXT_KEY"),
                "PortalAccessContextWebFilter should store resolved context into Reactor Context");
        assertTrue(source.contains("PortalAccessContextKeys.ATTRIBUTE_KEY"),
                "PortalAccessContextWebFilter should store resolved context into exchange attributes for error handling/logging");
    }

    @Test
    void onlyPortalAccessContextWebFilterInvokesResolvePortalAccessContextAtRuntime() throws Exception {
        List<Path> offenders;
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith("PortalAccessContextPort.java"))
                    .filter(path -> !path.endsWith("PortalAccessContextWebFilter.java"))
                    .filter(path -> contains(path, ".resolvePortalAccessContext("))
                    .toList();
        }

        assertTrue(offenders.isEmpty(),
                "Only PortalAccessContextWebFilter should invoke PortalAccessContextPort.resolvePortalAccessContext(...). "
                        + "Method declarations/implementations are allowed. Offenders: " + offenders);
    }

    @Test
    void applicationUseCasesDoNotDependOnPortalAccessContextPort() throws Exception {
        Path usecasePackage = Path.of("src/main/java/com/bct/ngtpa/apiservice/application/usecase");
        List<Path> offenders;
        try (Stream<Path> files = Files.walk(usecasePackage)) {
            offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, "PortalAccessContextPort"))
                    .toList();
        }

        assertTrue(offenders.isEmpty(),
                "Use cases should use CurrentPortalAccessContextProvider, not PortalAccessContextPort. Offenders: " + offenders);
    }

    @Test
    void selectedAccountUseCasesUseCurrentProviderNaming() throws Exception {
        assertUsesCurrentProvider("GetPersonalInformationService.java");
        assertUsesCurrentProvider("GetNotificationsService.java");
        assertUsesCurrentProvider("UpdateNotificationsReadStatusService.java");
        assertUsesCurrentProvider("GetContributionSummaryService.java");
        assertUsesCurrentProvider("ExportContributionSummaryService.java");
        assertUsesCurrentProvider("UpdatePersonalInformationService.java");
    }

    @Test
    void globalReferenceDataCountriesRemainsContextFree() throws Exception {
        Path controller = Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDataController.java");
        Path service = Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/GetReferenceDataCountriesService.java");

        String controllerSource = Files.readString(controller);
        String serviceSource = Files.readString(service);

        assertFalse(controllerSource.contains("PortalAccessContextPort"));
        assertFalse(controllerSource.contains("CurrentPortalAccessContextProvider"));
        assertFalse(controllerSource.contains("resolveRequiredAccountRef"));
        assertFalse(serviceSource.contains("PortalAccessContextPort"));
        assertFalse(serviceSource.contains("CurrentPortalAccessContextProvider"));
        assertFalse(serviceSource.contains("requireAccountRef"));
        assertTrue(serviceSource.contains("fetchCountryList()"),
                "Country list should remain the global APIM lookup boundary");
    }

    @Test
    void selectedAccountCommandsDoNotCarryAccountRef() throws Exception {
        assertCommandDoesNotCarryAccountRef("GetPersonalInformationCommand.java");
        assertCommandDoesNotCarryAccountRef("GetNotificationsCommand.java");
        assertCommandDoesNotCarryAccountRef("UpdateNotificationsReadStatusCommand.java");
        assertCommandDoesNotCarryAccountRef("GetContributionSummaryCommand.java");
        assertCommandDoesNotCarryAccountRef("ExportContributionSummaryCommand.java");
        assertCommandDoesNotCarryAccountRef("UpdatePersonalInformationCommand.java");
        assertCommandDoesNotCarryAccountRef("GetReferenceDataCountriesCommand.java");
    }

    private void assertUsesCurrentProvider(String fileName) throws IOException {
        Path path = Path.of("src/main/java/com/bct/ngtpa/apiservice/application/usecase").resolve(fileName);
        String source = Files.readString(path);
        assertTrue(source.contains("CurrentPortalAccessContextProvider"),
                fileName + " should depend on CurrentPortalAccessContextProvider");
        assertTrue(source.contains("currentPortalAccessContextProvider.current()"),
                fileName + " should read the current request context through current()");
        assertFalse(source.contains("PortalAccessContextPort"),
                fileName + " must not depend on the external resolution port");
        assertFalse(source.contains("resolvePortalAccessContext"),
                fileName + " must not resolve external context itself");
    }

    private void assertCommandDoesNotCarryAccountRef(String fileName) throws IOException {
        Path path = Path.of("src/main/java/com/bct/ngtpa/apiservice/application/dto").resolve(fileName);
        String source = Files.readString(path);
        assertFalse(source.contains("String accountRef,"), fileName + " must not have accountRef as a record component");
        assertFalse(source.contains("String accountRef)"), fileName + " must not have accountRef as sole record component");
        assertFalse(source.contains("accountRef()"), fileName + " must not expose accountRef accessor");
    }

    private boolean contains(Path path, String token) {
        try {
            return Files.readString(path).contains(token);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read " + path, exception);
        }
    }
}

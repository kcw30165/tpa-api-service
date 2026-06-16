package com.bct.ngtpa.apiservice.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PortalAccessContextFinalMigrationEvidenceTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path USECASE_PACKAGE = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/application/usecase");
    private static final Path DTO_PACKAGE = Path.of(
            "src/main/java/com/bct/ngtpa/apiservice/application/dto");

    @Test
    void externalPortalAccessContextResolutionIsCentralizedAtWebFilter() throws Exception {
        List<Path> callers;
        try (Stream<Path> files = Files.walk(MAIN_JAVA)) {
            callers = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, ".resolvePortalAccessContext("))
                    .toList();
        }

        assertTrue(callers.stream().anyMatch(path -> path.endsWith("PortalAccessContextWebFilter.java")),
                "PortalAccessContextWebFilter must call the external resolution port");
        assertTrue(callers.stream().allMatch(path -> path.endsWith("PortalAccessContextWebFilter.java")),
                "Only PortalAccessContextWebFilter may invoke PortalAccessContextPort.resolvePortalAccessContext(...). "
                        + "Callers: " + callers);
    }

    @Test
    void applicationUseCasesDoNotUseExternalPortalAccessContextPort() throws Exception {
        List<Path> offenders;
        try (Stream<Path> files = Files.walk(USECASE_PACKAGE)) {
            offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, "PortalAccessContextPort"))
                    .toList();
        }

        assertTrue(offenders.isEmpty(),
                "Application use cases should use CurrentPortalAccessContextProvider, not PortalAccessContextPort. "
                        + "Offenders: " + offenders);
    }

    @Test
    void selectedAccountServicesReadAlreadyResolvedCurrentContext() throws Exception {
        for (String service : selectedAccountServices()) {
            Path path = USECASE_PACKAGE.resolve(service);
            String source = Files.readString(path);

            assertTrue(source.contains("CurrentPortalAccessContextProvider"),
                    service + " should depend on CurrentPortalAccessContextProvider");
            assertTrue(source.contains("currentPortalAccessContextProvider.current()"),
                    service + " should read the current request PortalAccessContext");
            assertFalse(source.contains("resolvePortalAccessContext"),
                    service + " must not perform external context resolution");
            assertFalse(source.contains("PortalAccessContextPort"),
                    service + " must not depend on PortalAccessContextPort");
        }
    }

    @Test
    void selectedAccountCommandsDoNotExposeAccountRefAccessors() throws Exception {
        for (String command : accountScopedCommandDtos()) {
            Path path = DTO_PACKAGE.resolve(command);
            String source = Files.readString(path);

            assertFalse(source.contains("String accountRef,"), command + " must not carry accountRef as a record component");
            assertFalse(source.contains("String accountRef)"), command + " must not carry accountRef as sole record component");
            assertFalse(source.contains("accountRef()"), command + " must not expose accountRef accessor");
        }
    }

    @Test
    void updateAndReadControllersDoNotResolveSelectedAccountFromRequestHeaderContext() throws Exception {
        for (String controllerPath : selectedAccountControllers()) {
            Path path = Path.of(controllerPath);
            String source = Files.readString(path);

            assertFalse(source.contains("resolveRequiredAccountRef"),
                    path.getFileName() + " should not resolve selected account from RequestHeaderContext");
            assertFalse(source.contains("context.accountRef()"),
                    path.getFileName() + " should not read Account-Ref from RequestHeaderContext for command creation");
        }
    }

    @Test
    void referenceDataCountriesIsExplicitlyContextFree() throws Exception {
        Path controller = Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ReferenceDataController.java");
        Path service = USECASE_PACKAGE.resolve("GetReferenceDataCountriesService.java");

        String controllerSource = Files.readString(controller);
        String serviceSource = Files.readString(service);

        assertFalse(controllerSource.contains("CurrentPortalAccessContextProvider"));
        assertFalse(controllerSource.contains("PortalAccessContextPort"));
        assertFalse(serviceSource.contains("CurrentPortalAccessContextProvider"));
        assertFalse(serviceSource.contains("PortalAccessContextPort"));
        assertTrue(serviceSource.contains("fetchCountryList()"));
    }

    private List<String> selectedAccountServices() {
        return List.of(
                "GetPersonalInformationService.java",
                "UpdatePersonalInformationService.java",
                "GetNotificationsService.java",
                "UpdateNotificationsReadStatusService.java",
                "GetContributionSummaryService.java",
                "ExportContributionSummaryService.java");
    }

    private List<String> accountScopedCommandDtos() {
        return List.of(
                "GetPersonalInformationCommand.java",
                "UpdatePersonalInformationCommand.java",
                "GetNotificationsCommand.java",
                "UpdateNotificationsReadStatusCommand.java",
                "GetContributionSummaryCommand.java",
                "ExportContributionSummaryCommand.java",
                "GetReferenceDataCountriesCommand.java");
    }

    private List<String> selectedAccountControllers() {
        return List.of(
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/PersonalInformationController.java",
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/UpdatePersonalInformationController.java",
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/NotificationController.java",
                "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/controller/ContributionController.java");
    }

    private boolean contains(Path path, String token) {
        try {
            return Files.readString(path).contains(token);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read " + path, exception);
        }
    }
}

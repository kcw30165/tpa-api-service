package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Fail-first Stage 009 guard for authenticated country-list routing and executor ownership. */
class CountryListAuthenticatedExecutorBoundaryTest {
    private static final Path MAIN = Path.of("src/main/java/com/bct/ngtpa/apiservice");

    @Test
    void controllerCopiesTrustedSelectedAccountContextIntoExplicitCommand() throws Exception {
        String controller = read("adapter/in/web/controller/ReferenceDataController.java");
        String command = read("application/dto/GetReferenceDataCountriesCommand.java");

        assertThat(controller)
                .contains("CurrentPortalAccessContextResolver", "currentPortalAccessContextResolver.current()",
                        "portalAccessContext.account().accountEnv()",
                        "portalAccessContext.actor().actorUserRole()")
                .doesNotContain("PortalAccessContextPort", "resolvePortalAccessContext(",
                        "@RequestParam(\"accountEnv\")", "@RequestHeader(\"accountEnv\")",
                        "@RequestParam(\"actorUserRole\")", "@RequestHeader(\"actorUserRole\")");
        assertThat(command)
                .contains("String language", "String accountEnv", "ActorUserRole actorUserRole")
                .doesNotContain("accountRef");
    }

    @Test
    void servicePassesExplicitRoutingWithoutAmbientPortalContext() throws Exception {
        String service = read("application/usecase/GetReferenceDataCountriesService.java");
        String port = read("application/port/out/ApimReferenceDataCountriesPort.java");

        assertThat(service)
                .contains("fetchCountryList(command)")
                .doesNotContain("CurrentPortalAccessContextResolver", "PortalAccessContext",
                        "deferContextual", "reactor.util.context");
        assertThat(port)
                .contains("fetchCountryList(GetReferenceDataCountriesCommand command)")
                .doesNotContain("PortalAccessContext", "accountRef");
    }

    @Test
    void adapterUsesTypedExecutorWithAuthoritativeAuthenticatedRouting() throws Exception {
        String adapter = read("adapter/out/apim/ApimReferenceDataCountriesAdapter.java");

        assertThat(adapter)
                .contains("ApimExchangeExecutor", "ApimExchangeRequest", "postEnvelope(",
                        "GetCountryListApimRequest.class", "GetCountryListDataItem.class",
                        "ApimDatabaseScope.ACCOUNT_ENV", "command.actorUserRole()",
                        "command.accountEnv()", "ApimRetryEligibility.SAFE_BUSINESS_CALL",
                        "ApimResponseValidator.requireSuccessData(response)", "toCountryItems")
                .doesNotContain("ApimWebClientFacade", "ApimCertificateService",
                        "ApimPayloadCryptoService", "ApimCredentialProfileResolver", "ApimProperties",
                        "resolveProfile(", "getPublicKey(", "encryptRequest(", "decryptResponse",
                        "ActorUserRole.ROLE_USER_MBR", "ApimDatabaseScope.NON_ACCOUNT",
                        "StringUtils.hasText");
    }

    @Test
    void routingLedgerRecordsCountryListAsExecutorOwnedAccountEnvironmentOperation() throws Exception {
        String ledger = Files.readString(Path.of(
                "src/test/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimRoutingLedgerCharacterizationTest.java"));
        assertThat(ledger).contains(
                "e(\"TRPGetCountryList\", \"ApimReferenceDataCountriesAdapter.java\", \"ACCOUNT_ENV\", \"command.actorUserRole()\", \"command.accountEnv()\", \"SAFE_BUSINESS_CALL\", \"EXECUTOR\", \"ACCOUNT_ENV\")");
    }

    @Test
    void countryListMappingAndLanguageOwnershipRemainFeatureSpecific() throws Exception {
        String adapter = read("adapter/out/apim/ApimReferenceDataCountriesAdapter.java");
        String service = read("application/usecase/GetReferenceDataCountriesService.java");
        String controller = read("adapter/in/web/controller/ReferenceDataController.java");

        assertThat(adapter).contains("item.getCountryCode()", "item.getCountryNameEng()",
                "item.getCountryNameChi()", "item.getCallingCode()", "filter(Objects::nonNull)");
        assertThat(service).contains("ZH_HK", "countryNameChi()", "countryNameEng()",
                "ReferenceDataOption", "callingCodes");
        assertThat(controller).contains("ACCEPT_LANGUAGE_HEADER", "RequestLanguageResolver.resolve");
    }

    private static String read(String relative) throws Exception {
        return Files.readString(MAIN.resolve(relative));
    }
}

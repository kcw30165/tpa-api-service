package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class Stage021LegacyApimOrchestrationClosureFailFirstTest {
    private static final Path MAIN_APIM = Path.of("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim");

    @Test
    void featureOwnedApimSourcesHaveNoManualFoundationDependencies() throws Exception {
        List<Path> approvedFoundation = List.of(
                MAIN_APIM.resolve("execution"), MAIN_APIM.resolve("credential"), MAIN_APIM.resolve("retry"),
                MAIN_APIM.resolve("oauth"), MAIN_APIM.resolve("client"), MAIN_APIM.resolve("config"),
                MAIN_APIM.resolve("crypto"), MAIN_APIM.resolve("certificate"));
        List<String> approvedRootFiles = List.of(
                "ApimWebClientFacade.java", "ApimCertificateService.java", "ApimPayloadCryptoService.java",
                "ApimAppCertificateService.java");
        try (Stream<Path> files = Files.walk(MAIN_APIM)) {
            List<String> violations = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> approvedFoundation.stream().noneMatch(p::startsWith))
                    .filter(p -> !approvedRootFiles.contains(p.getFileName().toString()))
                    .flatMap(p -> forbiddenLines(p).stream())
                    .toList();
            assertThat(violations).isEmpty();
        }
    }

    @Test
    void forgotPasswordCompletionHasOneCanonicalExecutorOnlyProductionPath() throws Exception {
        String source = source("forgotpassword/ApimForgotPasswordCompletionAdapter.java");
        assertThat(source).contains("ApimExchangeExecutor", "postEnvelope(", "command.actorUserRole()")
                .doesNotContain("testTransport", "ApimForgotPasswordCompletionTransport transport",
                        "if(testTransport!=null)", "postResetPassword(");
        long constructors = source.lines()
                .filter(line -> line.contains("public ApimForgotPasswordCompletionAdapter("))
                .count();
        assertThat(constructors).isEqualTo(1);
    }

    @Test
    void migratedFeatureSourcesDoNotInferScopeOrSubstituteActorRole() throws Exception {
        try (Stream<Path> files = Files.walk(MAIN_APIM)) {
            List<String> violations = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.startsWith(MAIN_APIM.resolve("execution")))
                    .filter(p -> !p.startsWith(MAIN_APIM.resolve("credential")))
                    .flatMap(p -> inferenceLines(p).stream())
                    .toList();
            assertThat(violations).isEmpty();
        }
    }

    private static List<String> forbiddenLines(Path path) {
        try {
            String source = Files.readString(path);
            List<String> terms = List.of("ApimWebClientFacade", "ApimCertificateService",
                    "ApimPayloadCryptoService", "ApimCredentialProfileResolver", "resolveProfile(",
                    "getPublicKey(", "encryptRequest(", "decryptResponseEnvelope(",
                    "getEncryption().isEnabled()");
            return terms.stream().filter(source::contains).map(term -> path + " -> " + term).toList();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> inferenceLines(Path path) {
        try {
            String compact = Files.readString(path).replaceAll("\\s+", "");
            List<String> terms = List.of(
                    "accountEnv()==null?ApimDatabaseScope.NON_ACCOUNT",
                    "accountEnv()!=null?ApimDatabaseScope.ACCOUNT_ENV",
                    "ActorUserRole.ROLE_USER_MBR,null),ApimRetryEligibility",
                    "result.accountEnv(),ApimRetryEligibility",
                    "data.accountEnv(),ApimRetryEligibility");
            return terms.stream().filter(compact::contains).map(term -> path + " -> " + term).toList();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String source(String relative) throws Exception {
        return Files.readString(MAIN_APIM.resolve(relative));
    }
}

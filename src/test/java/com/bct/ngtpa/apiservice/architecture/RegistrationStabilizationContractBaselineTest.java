package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Fail-first baseline for 0040 registration stabilization.
 *
 * <p>
 * These tests intentionally describe the agreed target contract. They are
 * expected to fail against the pre-0040 production implementation and must not
 * be weakened to make the baseline green.
 * </p>
 */
class RegistrationStabilizationContractBaselineTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path REGISTRATION_YAML = Path.of("src/main/resources/application-page-registration.yml");
    private static final Pattern PUBLIC_ERROR_LITERAL = Pattern.compile(
            "\"(?:registration|workflow)\\.[A-Za-z0-9_.-]+\"");
    private static final Set<Integer> PRECISE_FAILURE_STATUSES = Set.of(400, 403, 409, 422, 429, 502, 503);

    @Test
    void contractMatrixUsesPreciseHttpPolicyAndErrPrefixedErrors() throws Exception {
        List<ContractRow> rows = contractRows();
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> {
            if (row.success()) {
                assertThat(row.httpStatus()).isEqualTo(200);
                assertThat(row.errorCode()).isBlank();
            } else {
                assertThat(row.httpStatus()).isIn(PRECISE_FAILURE_STATUSES);
                assertThat(row.httpStatus()).isNotEqualTo(200);
                assertThat(row.errorCode()).startsWith("err.");
            }
            if (row.retryAfter()) {
                assertThat(row.httpStatus()).isEqualTo(429);
            }
        });
    }

    @Test
    void registrationControllersExposeHttpStatusInsteadOfAlwaysReturningBodyOnly() throws Exception {
        for (Path controller : registrationControllers()) {
            String source = Files.readString(controller);
            assertThat(source)
                    .as("registration controller must expose ResponseEntity: %s", controller)
                    .contains("ResponseEntity<");
        }
    }

    @Test
    void registrationProductionCodeContainsNoUnprefixedPublicErrorLiterals() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Path file : registrationProductionFiles()) {
            String source = Files.readString(file);
            var matcher = PUBLIC_ERROR_LITERAL.matcher(source);
            while (matcher.find()) {
                String literal = matcher.group();
                if (!literal.contains("err.")
                        && !isOperationalEventLiteral(literal)) {
                    violations.add(file + " -> " + literal);
                }
            }
        }
        assertThat(violations)
                .as("public registration/workflow errors must be typed and err.-prefixed")
                .isEmpty();
    }

    @Test
    void registrationProductionCodeContainsNoEnumNameStringComparisons() throws Exception {
        List<Path> violations = registrationProductionFiles().stream()
                .filter(path -> read(path).contains(".status().name().equals("))
                .toList();
        assertThat(violations)
                .as("status mapping must be typed, never based on enum name strings")
                .isEmpty();
    }

    @Test
    void knownRegistrationApimErrorsAreMappedByExistingDownstreamClassification() throws Exception {
        String yaml = Files.readString(REGISTRATION_YAML);
        assertThat(yaml)
                .contains("endpoint: TRPSearchUnregMember")
                .contains("downstreamErrorCode: err.tpa.member.not.found")
                .doesNotContain("downstreamErrorCode: err.tpa.hkid.invalid")
                .contains("responseCode: err.registration.start.verificationFailed")
                .contains("endpoint: TRPRegMember")
                .contains("downstreamErrorCode: err.tpa.cust.id.duplicated")
                .contains("downstreamErrorCode: err.tpa.member.registered")
                .contains("responseCode: err.registration.username.isRegistered")
                .contains("endpoint: TRPChkCustId");
        assertThat(yaml).doesNotContain("err.tpa.username.taken");
    }

    @Test
    void errorAndNavigationRepresentationIsYamlOwned() throws Exception {
        String allYaml = Files.walk(Path.of("src/main/resources"))
                .filter(path -> path.toString().endsWith(".yml") || path.toString().endsWith(".yaml"))
                .map(RegistrationStabilizationContractBaselineTest::read)
                .reduce("", (left, right) -> left + "\n" + right);
                
        for (ContractRow row : contractRows()) {
            if (!row.success()) {
                assertThat(allYaml)
                        .as("YAML definition missing for %s", row.errorCode())
                        .contains(row.errorCode());
            }
        }
        assertThat(allYaml)
                .contains("nextStep:")
                .contains("targets:")
                .doesNotContain("messages:");
    }

    @Test
    void otpCancellationReasonIsTypedRatherThanAStringLiteral() throws Exception {
        Path service = Path.of(
                "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/cancel/"
                        + "CancelRegistrationService.java");
        String source = Files.readString(service);
        assertThat(source)
                .doesNotContain("\"USER_CANCELLED_REGISTRATION\"")
                .contains("OtpChallengeInvalidationReason.USER_CANCELLED_REGISTRATION");
    }

    @Test
    void registrationUsesNoPostLoginSchemeTypeContext() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Path file : registrationProductionFiles()) {
            String source = Files.readString(file);
            if (source.contains("schemeType") || source.contains("PortalAccessContext")) {
                violations.add(file.toString());
            }
        }
        assertThat(violations)
                .as("pre-login registration must not use schemeType or PortalAccessContext")
                .isEmpty();
    }

    private boolean isOperationalEventLiteral(String literal) {
        return "\"registration.lookup.downstream_contract_failure\""
                .equals(literal);
    }

    private List<ContractRow> contractRows() throws Exception {
        var resource = getClass().getClassLoader().getResourceAsStream(
                "contracts/registration-stabilization-http-contract.csv");
        assertThat(resource).isNotNull();
        try (var reader = new BufferedReader(
                new InputStreamReader(resource, StandardCharsets.UTF_8))) {
            return reader.lines().skip(1).filter(line -> !line.isBlank())
                    .map(this::parse).toList();
        }
    }

    private ContractRow parse(String line) {
        String[] values = line.split(",", -1);
        if (values.length != 8) {
            throw new IllegalStateException("Invalid registration contract row: " + line);
        }
        return new ContractRow(
                values[0], values[1], Integer.parseInt(values[2]),
                Boolean.parseBoolean(values[3]), values[4], values[5],
                Boolean.parseBoolean(values[6]), values[7]);
    }

    private List<Path> registrationControllers() throws Exception {
        try (var files = Files.walk(MAIN_JAVA)) {
            return files.filter(path -> path.getFileName().toString().startsWith("AuthRegistration"))
                    .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                    .sorted().toList();
        }
    }

    private List<Path> registrationProductionFiles() throws Exception {
        try (var files = Files.walk(MAIN_JAVA)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("registration")
                            || path.getFileName().toString().startsWith("AuthRegistration"))
                    .filter(path -> !path.toString().contains("pageconfig"))
                    .sorted().toList();
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read " + path, exception);
        }
    }

    private record ContractRow(
            String endpoint,
            String outcome,
            int httpStatus,
            boolean success,
            String apiStatus,
            String errorCode,
            boolean retryAfter,
            String cookieAction) {
    }
}

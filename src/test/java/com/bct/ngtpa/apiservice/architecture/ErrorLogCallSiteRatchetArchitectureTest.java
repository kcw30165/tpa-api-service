package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Scan-based ratchet guard over ERROR-level logging call sites in {@code src/main/java}.
 *
 * <p>Counts direct calls of the form {@code (log|logger|LOG|LOGGER).error(} or {@code .atError(},
 * after stripping block and line comments; {@code warn(} calls are never counted. {@link
 * com.bct.ngtpa.apiservice.adapter.in.web.error.ErrorEventLogger} and {@link
 * com.bct.ngtpa.apiservice.infrastructure.logging.StructuredFailureEventLogger} are the designated
 * owners of every ERROR event and are excluded from the scan entirely. {@link
 * com.bct.ngtpa.apiservice.adapter.in.web.filter.RequestLoggingWebFilter} is allowed exactly one
 * ERROR call (the escaped-exception event); any further ERROR call in that file is counted like any
 * other file.
 *
 * <p>{@code KNOWN_REMAINING} maps each remaining file's path, relative to the repository root, to
 * its exact current ERROR-call count. The guard fails if an unlisted file has an ERROR call, if a
 * listed file's count has risen, or if a listed file's count has fallen without the map being
 * corrected to match -- so the map always reflects the exact current count for every file with at
 * least one direct ERROR call, and can only shrink as call sites are migrated to the owners above.
 */
class ErrorLogCallSiteRatchetArchitectureTest {

  private static final Path SRC_MAIN_JAVA = Paths.get("src", "main", "java");

  private static final String ERROR_EVENT_LOGGER =
      "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/error/ErrorEventLogger.java";
  private static final String STRUCTURED_FAILURE_EVENT_LOGGER =
      "src/main/java/com/bct/ngtpa/apiservice/infrastructure/logging/StructuredFailureEventLogger.java";
  private static final String REQUEST_LOGGING_WEB_FILTER =
      "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/filter/RequestLoggingWebFilter.java";
  private static final int REQUEST_LOGGING_WEB_FILTER_ALLOWANCE = 1;

  private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
  private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");
  private static final Pattern ERROR_CALL =
      Pattern.compile("\\b(?:log|logger|LOG|LOGGER)\\.(?:error\\(|atError\\()");

  private static final Map<String, Integer> KNOWN_REMAINING = buildKnownRemaining();

  private static Map<String, Integer> buildKnownRemaining() {
    Map<String, Integer> map = new TreeMap<>();
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/login/LoginController.java", 3);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/auth/registration/mapper/"
            + "RegistrationStartWebMapper.java",
        1);
    map.put(REQUEST_LOGGING_WEB_FILTER, 1);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/mapper/"
            + "PersonalInformationUpdateWebMapper.java",
        1);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/adapter/in/web/mapper/"
            + "PersonalInformationWebMapper.java",
        6);
    map.put("src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/ApimWebClientFacade.java", 3);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/execution/"
            + "ApimExchangeOutcomeLogger.java",
        2);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/forgotpassword/"
            + "ApimForgotPasswordUsernameLookupAdapter.java",
        1);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/forgotpassword/mapper/"
            + "TrpChkCustIdForgotPasswordResultMapper.java",
        3);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/adapter/out/apim/fundfactsheet/"
            + "ApimFundFactSheetSourceAdapter.java",
        3);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/adapter/out/configservice/"
            + "ConfigServiceWebClientAdapter.java",
        3);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/application/service/auth/login/context/"
            + "DefaultLoginProtectedAuthFlowContextLoader.java",
        1);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/"
            + "detailsotp/ForgotPasswordDetailsOtpProtectedOperation.java",
        1);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/"
            + "detailsotp/ForgotPasswordUnderLockDetailsVerifier.java",
        1);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/otp/"
            + "resend/ForgotPasswordOtpResendProtectedOperation.java",
        1);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/otp/"
            + "resend/IdempotentResendForgotPasswordOtpService.java",
        1);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/forgotpassword/start/"
            + "ForgotPasswordStartProtectedOperation.java",
        2);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/resend/"
            + "LoginOtpResendService.java",
        1);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/otp/verify/"
            + "LoginOtpVerifyService.java",
        2);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/login/session/"
            + "EstablishAuthenticatedLoginSessionService.java",
        4);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/application/usecase/auth/registration/username/"
            + "CheckRegistrationUsernameService.java",
        2);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/application/usecase/fundcomparison/"
            + "FundComparisonPricesUseCase.java",
        1);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/application/usecase/session/authenticated/"
            + "AuthenticatedSessionLifecycleService.java",
        3);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/application/usecase/session/authenticated/"
            + "AuthenticatedSessionValidationService.java",
        1);
    map.put(
        "src/main/java/com/bct/ngtpa/apiservice/infrastructure/logging/"
            + "ExecutionLoggingAspect.java",
        1);
    return map;
  }

  @Test
  void errorCallSitesMatchExactlyTheRecordedKnownRemainingMap() throws IOException {
    Map<String, Integer> actual = scanErrorCallSites();

    assertThat(actual)
        .as(
            "Every file with a direct ERROR-level logging call must match its recorded "
                + "KNOWN_REMAINING count exactly. An unlisted file with an ERROR call, a count "
                + "that rose, or a count that fell without this map being corrected to match "
                + "all fail here. Migrate the call to the shared ERROR-level logger instead of "
                + "adding another direct call, or update KNOWN_REMAINING to the real count when "
                + "a call site is removed.")
        .containsExactlyInAnyOrderEntriesOf(KNOWN_REMAINING);
  }

  @Test
  void scanMechanismDetectsAtLeastOneErrorCallSite() throws IOException {
    int total = scanErrorCallSites().values().stream().mapToInt(Integer::intValue).sum();
    assertThat(total).isGreaterThan(0);
  }

  private static Map<String, Integer> scanErrorCallSites() throws IOException {
    Map<String, Integer> counts = new TreeMap<>();
    try (Stream<Path> paths = Files.walk(SRC_MAIN_JAVA)) {
      for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
        String relativePath = file.toString().replace('\\', '/');
        if (relativePath.equals(ERROR_EVENT_LOGGER)
            || relativePath.equals(STRUCTURED_FAILURE_EVENT_LOGGER)) {
          continue;
        }
        String content = Files.readString(file, StandardCharsets.UTF_8);
        int rawCount = countErrorCalls(stripComments(content));
        int allowance =
            relativePath.equals(REQUEST_LOGGING_WEB_FILTER)
                ? REQUEST_LOGGING_WEB_FILTER_ALLOWANCE
                : 0;
        int effective = Math.max(0, rawCount - allowance);
        if (effective > 0) {
          counts.put(relativePath, effective);
        }
      }
    }
    return counts;
  }

  private static String stripComments(String source) {
    String withoutBlockComments = BLOCK_COMMENT.matcher(source).replaceAll("");
    return LINE_COMMENT.matcher(withoutBlockComments).replaceAll("");
  }

  private static int countErrorCalls(String strippedSource) {
    Matcher matcher = ERROR_CALL.matcher(strippedSource);
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }
}

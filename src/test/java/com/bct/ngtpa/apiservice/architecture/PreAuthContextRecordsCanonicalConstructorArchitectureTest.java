package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The records of the pre-auth flows (Forgot Password, Login, Username Recovery, Registration, and the
 * auth-flow session model they share) declare only their canonical constructor.
 *
 * <p>A compatibility constructor lets a caller rebuild a record from an existing instance and
 * silently drop fields: the shape behind the Forgot Password details-check and resend failures,
 * where trustCode, schemeName and schemeNameChi were nulled by a shorter constructor. With only the
 * canonical constructor every call site names every component, so a rebuild that forgets a field no
 * longer compiles.
 *
 * <p>The test scans every record under those packages. {@code KNOWN_REMAINING} lists the records
 * that still declare a compatibility constructor, as {@code SimpleName:parameterCount}. It is a
 * ratchet: a new compatibility constructor fails the test, and so does a listed entry whose
 * constructor is gone, so each removal commit has to delete its entries and the list can only
 * shrink.
 */
class PreAuthContextRecordsCanonicalConstructorArchitectureTest {

  private static final String SOURCE_ROOT = "src/main/java/";
  private static final List<String> FLOW_SEGMENTS =
      List.of(
          "/forgotpassword/",
          "/login/",
          "/usernamerecovery/",
          "/registration/",
          "/session/authflow/");
  private static final int MINIMUM_RECORDS = 304;

  private static final Set<String> KNOWN_REMAINING =
      new TreeSet<>(List.of(
              "CompleteForgotPasswordCommand:10",
              "CredentialAuthenticationResult:6",
              "EstablishAuthenticatedLoginSessionCommand:5",
              "ForgotPasswordDetailsLookupResult:1",
              "ForgotPasswordIdempotencyReplaySnapshot:3",
              "ForgotPasswordOtpResendState:3",
              "ForgotPasswordPasswordSetupDataResponse:0",
              "ForgotPasswordTrpSearchMemberDataItem:9",
              "LoginPreAuthContext:5",
              "RegistrationLookupAccount:4",
              "RegistrationOtpResendResponse:4",
              "RegistrationProtectedAuthFlowContextCommand:5",
              "ResendRegistrationOtpResult:10",
              "SelectedTrust:5",
              "SimpleWebError:2",
              "StartForgotPasswordCommand:6",
              "StartForgotPasswordUsernameLookupCommand:2",
              "StartRegistrationCommand:10",
              "StartRegistrationError:2",
              "TrpChkCustIdApimDataItem:2",
              "TrpMemberLoginApimDataItem:4",
              "TrpSearchMemberApimDataItem:4",
              "TrpSearchMemberApimDataItem:9",
              "UsernameRecoveryStartResult:4",
              "VerifyForgotPasswordOtpResult:2",
              "VerifyLoginOtpResult:3",
              "VerifyLoginOtpResult:4"));

  @Test
  void thePreAuthPackagesAreScannedAndTheirRecordsLoad() throws IOException {
    List<Class<?>> records = records();
    List<String> names = records.stream().map(Class::getSimpleName).toList();

    assertThat(records.size() >= MINIMUM_RECORDS)
        .as("pre-auth records found (minimum " + MINIMUM_RECORDS + "): " + records.size())
        .isTrue();
    assertThat(
            names.containsAll(
                List.of(
                    "ForgotPasswordAuthFlowContext",
                    "LoginPreAuthContext",
                    "UsernameRecoveryAuthFlowContext",
                    "RegistrationAuthFlowContext")))
        .as("the four flow context records are among the scanned records")
        .isTrue();
  }

  @Test
  void noNewCompatibilityConstructorAppears() throws IOException {
    List<String> unexpected =
        offenders().stream().filter(entry -> !KNOWN_REMAINING.contains(entry)).toList();

    assertThat(unexpected)
        .as("pre-auth records declaring a non-canonical constructor that is not in KNOWN_REMAINING")
        .isEmpty();
  }

  @Test
  void everyKnownRemainingEntryStillExists() throws IOException {
    Set<String> found = offenders();
    List<String> stale =
        KNOWN_REMAINING.stream().filter(entry -> !found.contains(entry)).toList();

    assertThat(stale)
        .as("KNOWN_REMAINING entries whose compatibility constructor is gone; delete them")
        .isEmpty();
  }

  private static Set<String> offenders() throws IOException {
    Set<String> result = new TreeSet<>();
    for (Class<?> type : records()) {
      Class<?>[] canonical =
          Arrays.stream(type.getRecordComponents())
              .map(RecordComponent::getType)
              .toArray(Class<?>[]::new);
      for (Constructor<?> constructor : type.getDeclaredConstructors()) {
        if (!Arrays.equals(constructor.getParameterTypes(), canonical)) {
          result.add(type.getSimpleName() + ":" + constructor.getParameterCount());
        }
      }
    }
    return result;
  }

  private static List<Class<?>> records() throws IOException {
    List<Class<?>> records = new ArrayList<>();
    for (Class<?> type : topLevelTypes()) {
      collectRecords(type, records);
    }
    return records;
  }

  private static void collectRecords(Class<?> type, List<Class<?>> records) {
    try {
      if (type.isRecord()) {
        records.add(type);
      }
      for (Class<?> nested : type.getDeclaredClasses()) {
        collectRecords(nested, records);
      }
    } catch (LinkageError unloadable) {
      // a nested type that cannot be loaded carries no scanned record
    }
  }

  private static List<Class<?>> topLevelTypes() throws IOException {
    List<Path> files;
    try (Stream<Path> walk = Files.walk(Path.of(SOURCE_ROOT))) {
      files = walk.filter(path -> path.toString().endsWith(".java")).sorted().toList();
    }
    List<Class<?>> types = new ArrayList<>();
    for (Path file : files) {
      String normalized = file.toString().replace('\\', '/');
      if (FLOW_SEGMENTS.stream().noneMatch(normalized::contains)) {
        continue;
      }
      String name =
          normalized
              .substring(SOURCE_ROOT.length(), normalized.length() - ".java".length())
              .replace('/', '.');
      try {
        types.add(
            Class.forName(
                name,
                false,
                PreAuthContextRecordsCanonicalConstructorArchitectureTest.class.getClassLoader()));
      } catch (ClassNotFoundException | LinkageError notAType) {
        // a file whose type name differs from the file name carries no scanned record
      }
    }
    return types;
  }
}

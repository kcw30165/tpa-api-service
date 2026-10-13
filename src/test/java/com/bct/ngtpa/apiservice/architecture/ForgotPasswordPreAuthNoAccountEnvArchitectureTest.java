package com.bct.ngtpa.apiservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.application.dto.auth.forgotpassword.context.ForgotPasswordAuthFlowContext;
import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationResult;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthContext;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.details.ForgotPasswordDetailsLookupResult;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the rule that {@code accountEnv} does not exist in the Forgot Password pre-auth flow:
 * neither FE nor Java knows it before login, and nothing downstream of Forgot Password uses it (the
 * reset request carries only the customer id and the password, and its credential context is null).
 *
 * <p>Registration is deliberately NOT guarded: {@code TRPRegMember} needs {@code env} in its
 * request body and for APIM routing. Username Recovery returns {@code accountEnv} to FE at {@code
 * /start} for rendering, so only its {@code /request} path is guarded (it works from the Scheme-Ref
 * and needs only trustCode, schemeName and userName).
 */
class ForgotPasswordPreAuthNoAccountEnvArchitectureTest {

  private static final Path MAIN = Path.of("src/main/java");
  private static final String TOKEN = "accountenv";
  private static final String FORGOT_PASSWORD_PAYLOAD =
      "com.bct.ngtpa.apiservice.adapter.out.security.forgotpassword"
          + ".AesGcmForgotPasswordAuthFlowContextProtectorAdapter$Payload";

  @Test
  void forgotPasswordRecordsCarryNoAccountEnvComponent() {
    List<String> offenders = new ArrayList<>();
    for (Class<?> type :
        List.of(
            ForgotPasswordAuthFlowContext.class,
            ForgotPasswordDetailsLookupResult.class,
            ForgotPasswordCompletionCommand.class)) {
      offenders.addAll(accountEnvMembers(type));
    }

    assertThat(offenders)
        .as(
            "forgot-password context, lookup result and completion command members named accountEnv")
        .isEmpty();
  }

  @Test
  void forgotPasswordProtectedPayloadCarriesNoAccountEnv() throws ClassNotFoundException {
    Class<?> payload = Class.forName(FORGOT_PASSWORD_PAYLOAD);

    assertThat(payload.isRecord()).as("the protected payload is a record").isTrue();
    assertThat(accountEnvMembers(payload))
        .as("forgot-password protected payload members named accountEnv")
        .isEmpty();
  }

  @Test
  void noForgotPasswordMainSourceMentionsAccountEnv() throws IOException {
    List<String> offenders;
    try (Stream<Path> files = Files.walk(MAIN)) {
      offenders =
          files
              .filter(path -> path.toString().endsWith(".java"))
              .filter(path -> path.toString().toLowerCase(Locale.ROOT).contains("forgotpassword"))
              .filter(ForgotPasswordPreAuthNoAccountEnvArchitectureTest::mentionsAccountEnv)
              .map(path -> path.toString().replace('\\', '/'))
              .sorted()
              .toList();
    }

    assertThat(offenders)
        .as("forgot-password main source files that still mention accountEnv")
        .isEmpty();
  }

  @Test
  void usernameRecoveryRequestPathDoesNotReadAccountEnv() throws IOException {
    List<Path> matches;
    try (Stream<Path> files = Files.walk(MAIN)) {
      matches =
          files
              .filter(
                  path ->
                      path.getFileName()
                          .toString()
                          .equals("UsernameRecoveryRequestProtectedOperation.java"))
              .toList();
    }

    assertThat(matches.size() == 1)
        .as("exactly one UsernameRecoveryRequestProtectedOperation.java under " + MAIN)
        .isTrue();
    assertThat(mentionsAccountEnv(matches.get(0)))
        .as("the Username Recovery request operation mentions accountEnv")
        .isFalse();
  }

  @Test
  void loginPreAuthRecordsCarryNoAccountEnvMember() {
    List<String> offenders = new ArrayList<>();
    for (Class<?> type : List.of(LoginPreAuthContext.class, CredentialAuthenticationResult.class)) {
      offenders.addAll(accountEnvMembers(type));
    }

    assertThat(offenders)
        .as("login pre-auth context and credential authentication result members named accountEnv")
        .isEmpty();
  }

  private static List<String> accountEnvMembers(Class<?> type) {
    List<String> names =
        type.isRecord()
            ? Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList()
            : Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    return names.stream()
        .filter(name -> name.toLowerCase(Locale.ROOT).contains(TOKEN))
        .map(name -> type.getName() + "." + name)
        .toList();
  }

  private static boolean mentionsAccountEnv(Path path) {
    try {
      return Files.readString(path, StandardCharsets.UTF_8)
          .toLowerCase(Locale.ROOT)
          .contains(TOKEN);
    } catch (IOException exception) {
      throw new java.io.UncheckedIOException(exception);
    }
  }
}

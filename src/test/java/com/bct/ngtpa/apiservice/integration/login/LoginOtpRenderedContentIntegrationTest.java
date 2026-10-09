package com.bct.ngtpa.apiservice.integration.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.adapter.out.notice.YamlNoticeTemplateRegistryAdapter;
import com.bct.ngtpa.apiservice.adapter.out.notice.config.NoticeTemplateProperties;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthContext;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.LoginPreAuthDestination;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.CreateOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.InvalidateOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpReplacementOutcome;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.ReplaceOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.otp.VerifyOtpChallengeResult;
import com.bct.ngtpa.apiservice.application.dto.message.EmailMessage;
import com.bct.ngtpa.apiservice.application.dto.message.SenderResult;
import com.bct.ngtpa.apiservice.application.dto.message.SenderStatus;
import com.bct.ngtpa.apiservice.application.dto.message.SmsMessage;
import com.bct.ngtpa.apiservice.application.port.out.EmailSenderPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginOtpSessionBindingPort;
import com.bct.ngtpa.apiservice.application.port.out.LoginProtectedAuthFlowContextLoader;
import com.bct.ngtpa.apiservice.application.port.out.OtpChallengePort;
import com.bct.ngtpa.apiservice.application.port.out.SmsSenderPort;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.LoginOtpDestinationResolver;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.LoginOtpSendPolicy;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.LoginOtpSendService;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.LoginProtectedAuthFlowContextCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.LoginProtectedAuthFlowContextResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.SendLoginOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.SendLoginOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.SendLoginOtpStatus;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.LoginOtpResendService;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.ResendLoginOtpCommand;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.ResendLoginOtpResult;
import com.bct.ngtpa.apiservice.application.usecase.auth.login.otp.resend.ResendLoginOtpStatus;
import com.bct.ngtpa.apiservice.application.usecase.message.MessageDeliveryAdapter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import reactor.core.publisher.Mono;

/**
 * Login OTP content end to end (plan stage II-3.c and II-3.d combined, per Nick's instruction to
 * combine the two sub-stages): the REAL {@code application.yml}, the REAL {@link
 * YamlNoticeTemplateRegistryAdapter}, the REAL {@link LoginOtpSendService} / {@link
 * LoginOtpResendService} (as completed by scripts 100 and 102: {@code trustCode}/{@code
 * schemeName}/{@code schemeNameChi} are threaded from the context into the rendered content, with
 * a blank {@code schemeNameChi} falling back to {@code schemeName}), and the REAL {@link
 * MessageDeliveryAdapter} with its default renderer. Only the OTP challenge port, the session
 * binding port, the protected-context loader and the two senders are stubbed - exactly the
 * infrastructure-backed ports every existing Login unit test already stubs - so the text that
 * would reach a member can be asserted for SMS and EMAIL in en and zh_HK (II-3.c), and so a missing
 * registry definition is proven to fail before anything is created or sent (II-3.d).
 *
 * <p>{@link #realTrpMemberLoginResponseExampleRendersSuccessfullyOnAllFourChannels} uses the exact
 * field values from a real {@code TRPMemberLogin} response Nick provided (trust-code {@code
 * "JPM"}, sch-name {@code "JP MORGAN CHASE - JMCI"}, chin-sch-name blank): this is not a
 * hypothetical fixture, it is today's actual production shape, confirming the fallback (script
 * 102) closes the real gap it was built for end to end, through the real YAML.
 *
 * <p>{@link LoginOtpSendPolicy}'s real constructor (script 106, read-only) rejects a {@code
 * ttlSeconds} of zero or less and requires its {@code messageTemplate} to contain the literal
 * {@code {code}} placeholder; since the real registry definition - not this policy - supplies the
 * actual delivered subject/body (confirmed by the real, already-passing {@code
 * LoginOtpSendServiceTest}/{@code LoginOtpResendServiceTest}, which pass a deliberately-unused
 * placeholder string here, e.g. {@code "Policy template unused {code}"}), this fixture's own
 * {@code messageTemplate}/{@code ttlSeconds} values only need to satisfy that constructor, not
 * describe real content.
 */
class LoginOtpRenderedContentIntegrationTest {

  private static final String SMS_RECIPIENT = "91234567";
  private static final String EMAIL_RECIPIENT = "member@example.com";
  private static final String REAL_TRUST_CODE = "JPM";
  private static final String REAL_SCHEME_NAME = "JP MORGAN CHASE - JMCI";

  // ----- the real production response, exactly as given, on every channel -----------------------

  @Test
  void realTrpMemberLoginResponseExampleRendersSuccessfullyOnAllFourChannels() {
    // trust-code "JPM", sch-name "JP MORGAN CHASE - JMCI", chin-sch-name "" - the exact example.
    LoginPreAuthContext context = context(REAL_TRUST_CODE, REAL_SCHEME_NAME, "");

    Chain sms = new Chain(realProperties(), context);
    assertThat(sms.send(300, "en")).as("sms/en").isEqualTo(SendLoginOtpStatus.SENT);
    assertThat(sms.sms.get().content()).contains(REAL_TRUST_CODE).doesNotContain("{", "}");

    Chain smsZh = new Chain(realProperties(), context);
    assertThat(smsZh.send(300, "zh-HK")).as("sms/zh_HK").isEqualTo(SendLoginOtpStatus.SENT);
    assertThat(smsZh.sms.get().content()).contains(REAL_TRUST_CODE).doesNotContain("{", "}");

    Chain emailEn = new Chain(realProperties(), context);
    assertThat(emailEn.sendEmail(300, "en")).as("email/en").isEqualTo(SendLoginOtpStatus.SENT);
    assertThat(emailEn.email.get().subject()).contains(REAL_TRUST_CODE);
    assertThat(emailEn.email.get().body()).contains(REAL_SCHEME_NAME).doesNotContain("{", "}");

    Chain emailZh = new Chain(realProperties(), context);
    assertThat(emailZh.sendEmail(300, "zh-HK"))
        .as("email/zh_HK")
        .isEqualTo(SendLoginOtpStatus.SENT);
    assertThat(emailZh.email.get().subject())
        .doesNotContain(REAL_TRUST_CODE)
        .doesNotContain("{", "}");
    // the real gap script 102 closed: chin-sch-name was blank, so the body must fall back to
    // sch-name rather than rendering an empty Chinese scheme name.
    assertThat(emailZh.email.get().body()).contains(REAL_SCHEME_NAME).doesNotContain("{", "}");
  }

  @Test
  void resendRendersTheSameRealExampleCorrectlyOnEveryChannel() {
    LoginPreAuthContext context = context(REAL_TRUST_CODE, REAL_SCHEME_NAME, "");

    Chain sms = new Chain(realProperties(), context);
    assertThat(sms.resend("en")).as("resend sms/en").isEqualTo(ResendLoginOtpStatus.RESENT);
    assertThat(sms.sms.get().content()).contains(REAL_TRUST_CODE).doesNotContain("{", "}");

    Chain emailZh = new Chain(realProperties(), context);
    assertThat(emailZh.resendEmail("zh-HK"))
        .as("resend email/zh_HK")
        .isEqualTo(ResendLoginOtpStatus.RESENT);
    assertThat(emailZh.email.get().body()).contains(REAL_SCHEME_NAME).doesNotContain("{", "}");
  }

  // ----- a populated chin-sch-name is used as is, never overridden by the fallback ---------------

  @Test
  void aPopulatedSchemeNameChiIsRenderedAsIsNotReplacedByTheFallback() {
    LoginPreAuthContext context = context(REAL_TRUST_CODE, REAL_SCHEME_NAME, "示例計劃");

    Chain chain = new Chain(realProperties(), context);
    assertThat(chain.sendEmail(300, "zh-HK")).isEqualTo(SendLoginOtpStatus.SENT);

    assertThat(chain.email.get().body())
        .contains("示例計劃")
        .doesNotContain(REAL_SCHEME_NAME)
        .doesNotContain("{", "}");
  }

  // ----- the unit and the value: minutes, Chinese unit directly after the number, whole
  //       minutes with a minimum of one (D8) -----------------------------------------------

  @Test
  void hongKongSmsWritesTheChineseUnitDirectlyAfterTheNumberWithNoLeftoverPlaceholder() {
    Chain chain = new Chain(realProperties(), context(REAL_TRUST_CODE, REAL_SCHEME_NAME, ""));

    assertThat(chain.send(300, "zh-HK")).isEqualTo(SendLoginOtpStatus.SENT);

    assertThat(chain.sms.get().content())
        .contains("123456")
        .contains("有效期為5分鐘。")
        .doesNotContain("minutes")
        .doesNotContain("{", "}");
  }

  @Test
  void zhHkAndEnglishSmsContentActuallyDiffer() {
    LoginPreAuthContext context = context(REAL_TRUST_CODE, REAL_SCHEME_NAME, "");
    Chain english = new Chain(realProperties(), context);
    english.send(300, "en");
    Chain chinese = new Chain(realProperties(), context);
    chinese.send(300, "zh-HK");

    assertThat(chinese.sms.get().content()).isNotEqualTo(english.sms.get().content());
  }

  @Test
  void ttlValueIsWholeMinutesWithAMinimumOfOne() {
    // LoginOtpSendPolicy's real constructor (script 106) rejects ttlSeconds <= 0, so a policy
    // with ttlSeconds=0 can never exist in production; the floor-of-one-minute behavior
    // (Math.max(1, ttlSeconds / 60)) is proven just as validly starting from the smallest legal
    // value, 1 second, which still integer-divides to 0 and is still floored to 1 minute.
    int[][] cases = {{1, 1}, {30, 1}, {119, 1}, {120, 2}, {300, 5}, {600, 10}};
    LoginPreAuthContext context = context(REAL_TRUST_CODE, REAL_SCHEME_NAME, "");

    for (int[] pair : cases) {
      Chain chain = new Chain(realProperties(), context);
      chain.send(pair[0], "en");
      assertThat(chain.sms.get().content())
          .as("sms for %d seconds", pair[0])
          .contains("Valid for " + pair[1] + " ");
    }
  }

  // ----- II-3.d: a missing registry definition fails before anything is created or sent ----------

  @Test
  void aMissingLoginOtpDefinitionFailsSendBeforeAnyChallengeIsCreated() {
    NoticeTemplateProperties properties = realProperties();
    properties.setDefinitions(new LinkedHashMap<>());
    Chain chain = new Chain(properties, context(REAL_TRUST_CODE, REAL_SCHEME_NAME, ""));

    assertThatThrownBy(() -> chain.send(300, "en"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("login-otp");
    assertThat(chain.challengeCreateCalls.get()).isZero();
    assertThat(chain.smsCalls.get()).isZero();
  }

  @Test
  void aMissingLoginOtpDefinitionFailsResendBeforeAnyChallengeIsReplaced() {
    NoticeTemplateProperties properties = realProperties();
    properties.setDefinitions(new LinkedHashMap<>());
    Chain chain = new Chain(properties, context(REAL_TRUST_CODE, REAL_SCHEME_NAME, ""));

    assertThatThrownBy(() -> chain.resend("en"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("login-otp");
    assertThat(chain.challengeReplaceCalls.get()).isZero();
  }

  // ----- defensive: IF trustCode were ever absent (not today's real state - script 102 confirmed
  //       it is populated - but TAS has not guaranteed it always will be), SMS and the English
  //       email fail because the real YAML requires {trustCode} there; the Chinese email alone
  //       never needed {trustCode} and still succeeds as long as a scheme name is present --------

  @Test
  void aNullTrustCodeFailsSmsAndEnglishEmailButHongKongEmailStillSucceedsSinceItNeedsNoTrustCode() {
    LoginPreAuthContext context = context(null, REAL_SCHEME_NAME, "");

    Chain sms = new Chain(realProperties(), context);
    assertThat(sms.send(300, "en")).as("sms/en, null trustCode")
        .isEqualTo(SendLoginOtpStatus.DELIVERY_FAILED);

    Chain emailEn = new Chain(realProperties(), context);
    assertThat(emailEn.sendEmail(300, "en")).as("email/en, null trustCode (subject needs it)")
        .isEqualTo(SendLoginOtpStatus.DELIVERY_FAILED);

    Chain emailZh = new Chain(realProperties(), context);
    assertThat(emailZh.sendEmail(300, "zh-HK"))
        .as("email/zh_HK, null trustCode but schemeName present (neither the subject nor the"
            + " body needs trustCode)")
        .isEqualTo(SendLoginOtpStatus.SENT);
  }

  // ----- fixtures -----------------------------------------------------------------------------

  private static LoginPreAuthContext context(
      String trustCode, String schemeName, String schemeNameChi) {
    Map<String, LoginPreAuthDestination> destinations = new LinkedHashMap<>();
    destinations.put(
        "dst_sms", new LoginPreAuthDestination("dst_sms", "mobile", SMS_RECIPIENT, "****4567"));
    destinations.put(
        "dst_email",
        new LoginPreAuthDestination("dst_email", "email", EMAIL_RECIPIENT, "m***@example.com"));
    return new LoginPreAuthContext(
        "member01",
        "role_user",
        destinations,
        Instant.parse("2026-07-24T01:00:00Z").minusSeconds(60),
        "v1",
        trustCode,
        schemeName,
        schemeNameChi);
  }

  private static NoticeTemplateProperties realProperties() {
    YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
    List<PropertySource<?>> sources;
    try {
      sources = loader.load("application.yml", new ClassPathResource("application.yml"));
    } catch (Exception e) {
      throw new IllegalStateException("failed to load application.yml from test classpath", e);
    }
    MutablePropertySources propertySources = new MutablePropertySources();
    sources.forEach(propertySources::addLast);
    Binder binder = new Binder(ConfigurationPropertySources.from(propertySources));
    return binder
        .bind("notice-templates", NoticeTemplateProperties.class)
        .orElseThrow(
            () -> new IllegalStateException("notice-templates did not bind from application.yml"));
  }

  /**
   * The real registry, the real send/resend services and the real delivery adapter, wired to one
   * fixed, chosen destination; everything else recorded or stubbed.
   */
  private static final class Chain {
    final AtomicInteger smsCalls = new AtomicInteger();
    final AtomicInteger emailCalls = new AtomicInteger();
    final AtomicInteger challengeCreateCalls = new AtomicInteger();
    final AtomicInteger challengeReplaceCalls = new AtomicInteger();
    final AtomicReference<SmsMessage> sms = new AtomicReference<>();
    final AtomicReference<EmailMessage> email = new AtomicReference<>();
    private final LoginPreAuthContext context;
    private final YamlNoticeTemplateRegistryAdapter registry;
    private final MessageDeliveryAdapter delivery;

    Chain(NoticeTemplateProperties properties, LoginPreAuthContext context) {
      this.context = context;
      this.registry = new YamlNoticeTemplateRegistryAdapter(properties);
      SmsSenderPort sms =
          message -> {
            smsCalls.incrementAndGet();
            this.sms.set(message);
            return Mono.just(
                new SenderResult(SenderStatus.ACCEPTED, "provider-sms-id", null, null));
          };
      EmailSenderPort email =
          message -> {
            emailCalls.incrementAndGet();
            this.email.set(message);
            return Mono.just(
                new SenderResult(SenderStatus.ACCEPTED, "provider-email-id", null, null));
          };
      this.delivery = new MessageDeliveryAdapter(sms, email);
    }

    SendLoginOtpStatus send(int ttlSeconds, String locale) {
      return sendWithDestination("dst_sms", ttlSeconds, locale).status();
    }

    SendLoginOtpStatus sendEmail(int ttlSeconds, String locale) {
      return sendWithDestination("dst_email", ttlSeconds, locale).status();
    }

    private SendLoginOtpResult sendWithDestination(
        String destinationId, int ttlSeconds, String locale) {
      var service = sendService(ttlSeconds);
      var command =
          new SendLoginOtpCommand(
              "afs_opaque",
              "csrf_opaque",
              destinationId,
              "idem_opaque",
              null,
              null,
              locale,
              "req-1");
      return service.send(command).block();
    }

    ResendLoginOtpStatus resend(String locale) {
      return resendWithDestination("dst_sms", locale).status();
    }

    ResendLoginOtpStatus resendEmail(String locale) {
      return resendWithDestination("dst_email", locale).status();
    }

    private ResendLoginOtpResult resendWithDestination(String destinationId, String locale) {
      var service = resendService(destinationId);
      var command =
          new ResendLoginOtpCommand(
              "afs_opaque", "csrf_opaque", "idem_opaque", null, null, locale, "req-1");
      return service.resend(command).block();
    }

    private LoginOtpSendService sendService(int ttlSeconds) {
      LoginProtectedAuthFlowContextLoader loader =
          command ->
              Mono.just(
                  LoginProtectedAuthFlowContextResult.valid(context, null, null, "csrf_hash"));
      OtpChallengePort otp =
          new OtpChallengePort() {
            public Mono<CreateOtpChallengeResult> createChallenge(CreateOtpChallengeCommand c) {
              challengeCreateCalls.incrementAndGet();
              return Mono.just(
                  new CreateOtpChallengeResult(
                      "otp_opaque", "123456", Instant.now().plusSeconds(ttlSeconds), ttlSeconds));
            }

            public Mono<VerifyOtpChallengeResult> verifyChallenge(VerifyOtpChallengeCommand c) {
              return Mono.error(new UnsupportedOperationException());
            }

            public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand c) {
              return Mono.error(new UnsupportedOperationException());
            }

            public Mono<Void> invalidateChallenge(InvalidateOtpChallengeCommand c) {
              return Mono.empty();
            }
          };
      LoginOtpSessionBindingPort binding = command -> Mono.empty();
      return new LoginOtpSendService(
          loader,
          new LoginOtpDestinationResolver(),
          otp,
          delivery,
          binding,
          (type, value) -> "hash_only",
          Clock.fixed(Instant.parse("2026-07-24T01:00:00Z"), ZoneOffset.UTC),
          new LoginOtpSendPolicy(6, true, ttlSeconds, 60, 5, false, 0),
          registry);
    }

    private LoginOtpResendService resendService(String destinationId) {
      LoginPreAuthDestination selected = context.destinationsById().get(destinationId);
      LoginProtectedAuthFlowContextLoader loader =
          command ->
              Mono.just(
                  LoginProtectedAuthFlowContextResult.valid(
                      context, "otp_opaque", selected, "csrf_hash"));
      OtpChallengePort otp =
          new OtpChallengePort() {
            public Mono<CreateOtpChallengeResult> createChallenge(CreateOtpChallengeCommand c) {
              return Mono.error(new UnsupportedOperationException());
            }

            public Mono<VerifyOtpChallengeResult> verifyChallenge(VerifyOtpChallengeCommand c) {
              return Mono.error(new UnsupportedOperationException());
            }

            public Mono<ReplaceOtpChallengeResult> replaceChallenge(ReplaceOtpChallengeCommand c) {
              challengeReplaceCalls.incrementAndGet();
              return Mono.just(
                  new ReplaceOtpChallengeResult(
                      "otp_opaque",
                      "otp_new",
                      "123456",
                      OtpReplacementOutcome.REPLACED,
                      Instant.now().plusSeconds(300),
                      300,
                      60,
                      0,
                      null));
            }

            public Mono<Void> invalidateChallenge(InvalidateOtpChallengeCommand c) {
              return Mono.empty();
            }
          };
      LoginOtpSessionBindingPort binding = command -> Mono.empty();
      return new LoginOtpResendService(
          loader,
          otp,
          delivery,
          binding,
          Clock.fixed(Instant.parse("2026-07-24T01:00:00Z"), ZoneOffset.UTC),
          new LoginOtpSendPolicy(6, true, 300, 60, 5, false, 0),
          registry);
    }
  }
}

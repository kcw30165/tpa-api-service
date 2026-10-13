package com.bct.ngtpa.apiservice.adapter.out.notice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

/**
 * Pins the exact text of the eight e-mail bodies in {@code src/main/resources/application.yml} to
 * the BRD layout (E0001, E0002, E0003): each BRD paragraph is a line, each empty paragraph a blank
 * line, and the horizontal rule under the "RE:" or "關於" line is a separator line of hyphens. The
 * e-mail adapter sends plain text, so the BRD's italic line and a true rule are not reproduced.
 *
 * <p>The Chinese subjects of Login, Forgot Password and Username Recovery start with the trust code
 * placeholder. In the Registration, Login and Forgot Password English bodies the expiry line
 * follows the one-time-password line directly, and the one-time-password line ends in three spaces:
 * Outlook removes a single line break from plain-text mail and keeps this one.
 */
class ApplicationYamlEmailBodiesMatchBrdTest {

  /** Outlook keeps the line break after a line that ends in three spaces. */
  private static final String OTP_LINE_END = " ".repeat(3);

  private static Properties properties;

  @BeforeAll
  static void loadTheMainApplicationYaml() {
    YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
    factory.setResources(new FileSystemResource("src/main/resources/application.yml"));
    properties = factory.getObject();
    assertThat(properties).isNotNull();
  }

  private static String body(String definition, String locale) {
    return properties.getProperty(
        "notice-templates.definitions." + definition + ".email." + locale + ".body");
  }

  private static String subject(String definition, String locale) {
    return properties.getProperty(
        "notice-templates.definitions." + definition + ".email." + locale + ".subject");
  }

  private static String expected(String... lines) {
    return String.join("\n", lines) + "\n";
  }

  @Test
  void registrationOtpEnglishBodyMatchesTheBrdLayout() {
    assertThat(body("registration-otp", "en"))
        .isEqualTo(
            expected(
                "Dear Member,",
                "",
                "RE: BCT Pension Member Portal- Account Registration",
                "------------------------------------------------------------",
                "",
                "Your One-Time Password (OTP): {code}" + OTP_LINE_END,
                "This password expires in {ttlValue} {ttlUnit}.",
                "",
                "Bank Consortium Trust Company Limited",
                "",
                "[This is an automatically generated e-mail. Please do not reply to this e-mail address. Thank you for your co-operation.]"));
  }

  @Test
  void registrationOtpChineseBodyMatchesTheBrdLayout() {
    assertThat(body("registration-otp", "zh_HK"))
        .isEqualTo(
            expected(
                "關於：BCT 銀聯集團 退休金會員平台 - 帳戶登記",
                "------------------------------------------------------------",
                "",
                "您的一次性密碼(OTP)為：{code}",
                "此密碼於{ttlValue}{ttlUnit}內有效。",
                "",
                "銀聯信託有限公司",
                "",
                "［此乃經自動系統發出之電子郵件，請勿回覆。多謝合作。］"));
  }

  @Test
  void loginOtpEnglishBodyMatchesTheBrdLayout() {
    assertThat(body("login-otp", "en"))
        .isEqualTo(
            expected(
                "Dear Member,",
                "",
                "RE: {schemeName} (\"the Plan\")",
                "------------------------------------------------------------",
                "",
                "Your One-Time Password (OTP) for BCT Pension Member Portal is: {code}"
                    + OTP_LINE_END,
                "This password expires in {ttlValue} {ttlUnit}.",
                "",
                "Bank Consortium Trust Company Limited",
                "(Trustee and Administrator of the Plan)",
                "",
                "[This is an automatically generated e-mail. Please do not reply to this e-mail address. Thank you for your co-operation.]"));
  }

  @Test
  void loginOtpChineseBodyMatchesTheBrdLayout() {
    assertThat(body("login-otp", "zh_HK"))
        .isEqualTo(
            expected(
                "關於：{schemeNameChi}（「本計劃」）",
                "------------------------------------------------------------",
                "",
                "您的退休金會員平台的一次性密碼(OTP)為：{code}",
                "此密碼於{ttlValue}{ttlUnit}內有效。",
                "",
                "銀聯信託有限公司",
                "（本計劃的信託人及行政管理人)",
                "",
                "［此乃經自動系統發出之電子郵件，請勿回覆。多謝合作。］"));
  }

  @Test
  void forgotPasswordOtpEnglishBodyMatchesTheBrdLayout() {
    assertThat(body("forgot-password-otp", "en"))
        .isEqualTo(
            expected(
                "Dear Member,",
                "",
                "RE: {schemeName} (\"the Plan\")",
                "------------------------------------------------------------",
                "",
                "Your One-Time Password (OTP) for BCT Pension Member Portal is: {code}"
                    + OTP_LINE_END,
                "This password expires in {ttlValue} {ttlUnit}.",
                "",
                "Bank Consortium Trust Company Limited",
                "(Trustee and Administrator of the Plan)",
                "",
                "[This is an automatically generated e-mail. Please do not reply to this e-mail address. Thank you for your co-operation.]"));
  }

  @Test
  void forgotPasswordOtpChineseBodyMatchesTheBrdLayout() {
    assertThat(body("forgot-password-otp", "zh_HK"))
        .isEqualTo(
            expected(
                "關於：{schemeNameChi}（「本計劃」）",
                "------------------------------------------------------------",
                "",
                "您的退休金會員平台的一次性密碼(OTP)為：{code}",
                "此密碼於{ttlValue}{ttlUnit}內有效。",
                "",
                "銀聯信託有限公司",
                "（本計劃的信託人及行政管理人)",
                "",
                "［此乃經自動系統發出之電子郵件，請勿回覆。多謝合作。］"));
  }

  @Test
  void usernameRecoveryEnglishBodyMatchesTheBrdLayout() {
    assertThat(body("username-recovery", "en"))
        .isEqualTo(
            expected(
                "Dear Member,",
                "",
                "RE: {schemeName} (\"the Plan\")",
                "------------------------------------------------------------",
                "",
                "Your username of the above-mentioned Plan as below",
                "",
                "{username}",
                "",
                "Bank Consortium Trust Company Limited",
                "(Trustee and Administrator of the Plan)",
                "",
                "[This is an automatically generated e-mail. Please do not reply to this e-mail address. Thank you for your co-operation.]"));
  }

  @Test
  void usernameRecoveryChineseBodyMatchesTheBrdLayout() {
    assertThat(body("username-recovery", "zh_HK"))
        .isEqualTo(
            expected(
                "親愛的成員：",
                "",
                "關於：{schemeNameChi}（「本計劃」）",
                "------------------------------------------------------------",
                "",
                "您於上述計劃的用戶名稱如下：",
                "",
                "{username}",
                "",
                "銀聯信託有限公司",
                "（本計劃的信託人及行政管理人)",
                "",
                "［此乃經自動系統發出之電子郵件，請勿回覆。多謝合作。］"));
  }

  @Test
  void theChineseSubjectsOfLoginForgotPasswordAndUsernameRecoveryStartWithTheTrustCode() {
    assertThat(subject("login-otp", "zh_HK")).isEqualTo("{trustCode} BCT 退休計劃：成員的一次性密碼");
    assertThat(subject("forgot-password-otp", "zh_HK")).isEqualTo("{trustCode} BCT 退休計劃：成員的一次性密碼");
    assertThat(subject("username-recovery", "zh_HK")).isEqualTo("{trustCode} BCT 退休計劃 - 取回用戶名稱");
  }

  @Test
  void theEnglishSmsPrefixesAreBctDashTrustCode() {
    for (String definition : List.of("login-otp", "forgot-password-otp", "username-recovery")) {
      assertThat(properties.getProperty("notice-templates.definitions." + definition + ".sms.en"))
          .as(definition)
          .startsWith("BCT-{trustCode}: ");
    }
  }
}

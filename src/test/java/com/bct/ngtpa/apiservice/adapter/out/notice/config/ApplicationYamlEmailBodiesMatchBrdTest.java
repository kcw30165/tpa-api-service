package com.bct.ngtpa.apiservice.adapter.out.notice.config;

import static org.assertj.core.api.Assertions.assertThat;

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
 * <p>The Chinese subjects of Login, Forgot Password and Username Recovery carry no trust code.
 */
class ApplicationYamlEmailBodiesMatchBrdTest {

  private static final String RULE = "-".repeat(60);

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
                RULE,
                "",
                "Your One-Time Password (OTP): {code}",
                "",
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
                RULE,
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
                RULE,
                "",
                "Your One-Time Password (OTP) for BCT Pension Member Portal is: {code}",
                "",
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
                RULE,
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
                RULE,
                "",
                "Your One-Time Password (OTP) for BCT Pension Member Portal is: {code}",
                "",
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
                RULE,
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
                RULE,
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
                RULE,
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
  void theChineseSubjectsOfLoginForgotPasswordAndUsernameRecoveryCarryNoTrustCode() {
    assertThat(subject("login-otp", "zh_HK"))
        .isEqualTo("BCT 退休計劃：成員的一次性密碼")
        .doesNotContain("{trustCode}");
    assertThat(subject("forgot-password-otp", "zh_HK"))
        .isEqualTo("BCT 退休計劃：成員的一次性密碼")
        .doesNotContain("{trustCode}");
    assertThat(subject("username-recovery", "zh_HK"))
        .isEqualTo("BCT 退休計劃 - 取回用戶名稱")
        .doesNotContain("{trustCode}");
  }
}

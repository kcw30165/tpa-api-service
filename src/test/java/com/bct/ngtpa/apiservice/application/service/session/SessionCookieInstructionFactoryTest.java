package com.bct.ngtpa.apiservice.application.service.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieOperation;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookiePolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieSameSite;
import org.junit.jupiter.api.Test;

class SessionCookieInstructionFactoryTest {

  private final SessionCookieInstructionFactory factory = new SessionCookieInstructionFactory();

  @Test
  void createsSetCookieInstruction() {
    var policy = authFlowCookiePolicy();

    var instruction = factory.setCookie(policy, "afs_abc");

    assertThat(instruction.operation()).isEqualTo(SessionCookieOperation.SET);
    assertThat(instruction.policy()).isSameAs(policy);
    assertThat(instruction.value()).isEqualTo("afs_abc");
  }

  @Test
  void createsClearCookieInstruction() {
    var policy = authFlowCookiePolicy();

    var instruction = factory.clearCookie(policy);

    assertThat(instruction.operation()).isEqualTo(SessionCookieOperation.CLEAR);
    assertThat(instruction.policy()).isSameAs(policy);
    assertThat(instruction.value()).isNull();
  }

  @Test
  void setCookieInstructionRejectsBlankValue() {
    assertThatThrownBy(() -> factory.setCookie(authFlowCookiePolicy(), " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cookie value is required for SET operation")
        .hasMessageNotContaining("afs_");
  }

  @Test
  void setCookieInstructionRejectsNullPolicy() {
    assertThatThrownBy(() -> factory.setCookie(null, "afs_abc"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("policy must not be null");
  }

  @Test
  void sameSiteNoneRequiresSecure() {
    assertThatThrownBy(
            () ->
                new SessionCookiePolicy(
                    "auth_flow_sid", "/api/v1/auth", 1800, true, false, SessionCookieSameSite.NONE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SameSite=None cookies must be Secure");
  }

  private static SessionCookiePolicy authFlowCookiePolicy() {
    return new SessionCookiePolicy(
        "auth_flow_sid", "/api/v1/auth", 1800, true, true, SessionCookieSameSite.NONE);
  }
}

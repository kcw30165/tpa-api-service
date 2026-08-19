package com.bct.ngtpa.apiservice.adapter.in.web.session.cookie;

import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieInstruction;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieOperation;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookiePolicy;
import com.bct.ngtpa.apiservice.application.dto.session.SessionCookieSameSite;
import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;

public class HttpSessionCookieAdapter {

  public void apply(ServerHttpResponse response, SessionCookieInstruction instruction) {
    Objects.requireNonNull(response, "response must not be null");
    Objects.requireNonNull(instruction, "instruction must not be null");

    SessionCookiePolicy policy = instruction.policy();
    ResponseCookie cookie =
        ResponseCookie.from(policy.name(), cookieValue(instruction))
            .path(policy.path())
            .maxAge(maxAge(instruction))
            .httpOnly(policy.httpOnly())
            .secure(policy.secure())
            .sameSite(sameSiteWireValue(policy.sameSite()))
            .build();

    response.addCookie(cookie);
  }

  public void applyAll(
      ServerHttpResponse response, Collection<SessionCookieInstruction> instructions) {
    Objects.requireNonNull(response, "response must not be null");
    Objects.requireNonNull(instructions, "instructions must not be null");
    instructions.forEach(instruction -> apply(response, instruction));
  }

  private String cookieValue(SessionCookieInstruction instruction) {
    if (instruction.operation() == SessionCookieOperation.CLEAR) {
      return "";
    }
    return instruction.value();
  }

  private Duration maxAge(SessionCookieInstruction instruction) {
    if (instruction.operation() == SessionCookieOperation.CLEAR) {
      return Duration.ZERO;
    }
    return Duration.ofSeconds(instruction.policy().maxAgeSeconds());
  }

  private String sameSiteWireValue(SessionCookieSameSite sameSite) {
    return switch (sameSite) {
      case NONE -> "None";
      case LAX -> "Lax";
      case STRICT -> "Strict";
    };
  }
}

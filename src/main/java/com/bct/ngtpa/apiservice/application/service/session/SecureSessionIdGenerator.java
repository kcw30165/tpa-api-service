package com.bct.ngtpa.apiservice.application.service.session;

import com.bct.ngtpa.apiservice.application.dto.session.SessionId;
import com.bct.ngtpa.apiservice.application.dto.session.SessionIdGenerationCommand;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

public class SecureSessionIdGenerator implements SessionIdGenerator {

  private final SecureRandom secureRandom;

  public SecureSessionIdGenerator() {
    this(new SecureRandom());
  }

  SecureSessionIdGenerator(SecureRandom secureRandom) {
    this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
  }

  @Override
  public SessionId generate(SessionIdGenerationCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    byte[] randomBytes = new byte[command.entropyBytes()];
    secureRandom.nextBytes(randomBytes);
    String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    return new SessionId(command.prefix() + "_" + randomPart);
  }
}

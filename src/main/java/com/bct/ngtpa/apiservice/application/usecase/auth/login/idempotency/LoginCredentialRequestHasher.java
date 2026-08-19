package com.bct.ngtpa.apiservice.application.usecase.auth.login.idempotency;

import com.bct.ngtpa.apiservice.application.dto.auth.login.CredentialAuthenticationCommand;
import com.bct.ngtpa.apiservice.application.dto.auth.login.idempotency.LoginCredentialRequestHash;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

public final class LoginCredentialRequestHasher {
  public LoginCredentialRequestHash hash(CredentialAuthenticationCommand c) {
    Objects.requireNonNull(c);
    try {
      MessageDigest d = MessageDigest.getInstance("SHA-256");
      update(d, c.username());
      d.update((byte) 0);
      update(d, c.password());
      return new LoginCredentialRequestHash(HexFormat.of().formatHex(d.digest()));
    } catch (Exception e) {
      throw new IllegalStateException("Unable to hash login credential request", e);
    }
  }

  private void update(MessageDigest d, String v) {
    if (v == null) throw new IllegalArgumentException("Credential hash input must not be null");
    d.update(v.getBytes(StandardCharsets.UTF_8));
  }
}

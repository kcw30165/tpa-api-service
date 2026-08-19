package com.bct.ngtpa.apiservice.adapter.in.web.auth.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginAccountIdentifierProperties {
  private final Identifier policyNo = new Identifier();
  private final Identifier certNo = new Identifier();
  private String padCharacter = "0";

  @Getter
  @Setter
  public static class Identifier {
    private int width;
  }

  public void validate() {
    if (policyNo.getWidth() <= 0 || certNo.getWidth() <= 0)
      throw new IllegalStateException("Login account identifier widths must be positive");
    if (padCharacter == null
        || padCharacter.length() != 1
        || !Character.isDigit(padCharacter.charAt(0)))
      throw new IllegalStateException("Login account identifier pad-character must be one digit");
  }
}

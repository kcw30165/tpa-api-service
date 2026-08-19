package com.bct.ngtpa.apiservice.adapter.out.email;

import org.springframework.util.StringUtils;

public final class EmailAddressMasker {

  private EmailAddressMasker() {}

  public static String mask(String emailAddress) {
    if (!StringUtils.hasText(emailAddress)) {
      return "";
    }
    String trimmed = emailAddress.trim();
    int at = trimmed.indexOf('@');
    if (at <= 0 || at == trimmed.length() - 1) {
      return "***";
    }
    String local = trimmed.substring(0, at);
    String domain = trimmed.substring(at);
    if (local.length() <= 2) {
      return "***" + domain;
    }
    return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
  }
}

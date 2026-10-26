package com.bct.ngtpa.apiservice.application.service.account;

public final class AccountIdentifierFormatter {
  private final int policyWidth;
  private final int certificateWidth;
  private final char padCharacter;

  public AccountIdentifierFormatter(int policyWidth, int certificateWidth, char padCharacter) {
    if (policyWidth <= 0 || certificateWidth <= 0) {
      throw new IllegalArgumentException("identifier widths must be positive");
    }
    if (!Character.isDigit(padCharacter)) {
      throw new IllegalArgumentException("padCharacter must be numeric");
    }
    this.policyWidth = policyWidth;
    this.certificateWidth = certificateWidth;
    this.padCharacter = padCharacter;
  }

  public String format(String policyNo, String certificateNo) {
    return formatPolicyNo(policyNo) + "-" + formatCertificateNo(certificateNo);
  }

  public String formatPolicyNo(String policyNo) {
    return leftPad(required(policyNo, "policyNo"), policyWidth);
  }

  public String formatCertificateNo(String certificateNo) {
    String value = required(certificateNo, "certificateNo");
    if (!value.matches("[0-9]+") || value.chars().allMatch(character -> character == '0')) {
      throw new IllegalArgumentException("certificateNo must be a positive number");
    }
    return leftPad(value, certificateWidth);
  }

  public int policyWidth() {
    return policyWidth;
  }

  public int certificateWidth() {
    return certificateWidth;
  }

  private String required(String value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return trimmed;
  }

  private String leftPad(String value, int width) {
    if (value.length() > width) {
      throw new IllegalArgumentException("identifier exceeds configured width");
    }
    return String.valueOf(padCharacter).repeat(width - value.length()) + value;
  }
}

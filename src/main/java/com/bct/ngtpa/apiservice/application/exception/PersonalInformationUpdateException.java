package com.bct.ngtpa.apiservice.application.exception;

public final class PersonalInformationUpdateException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  private PersonalInformationUpdateException() {
    super("Personal information update unavailable");
  }

  public static PersonalInformationUpdateException systemFailure() {
    return new PersonalInformationUpdateException();
  }
}

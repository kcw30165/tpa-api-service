package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.TrpResetPasswordApimEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.dto.TrpResetPasswordApimDataItem;
import com.bct.ngtpa.apiservice.application.dto.observability.FailureOrigin;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionResult;
import com.bct.ngtpa.apiservice.shared.identity.IdentifierNormalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Objects;

public final class TrpResetPasswordApimResultMapper {
  private static final String PASSWORD_INVALID = "err.tpa.password.invalid";
  private static final String PASSWORD_REUSED = "err.tpa.password.used.in.last.5.changes.";
  private static final String MEMBER_NOT_FOUND = "err.tpa.member.not.found";
  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
  private static final DateTimeFormatter TIME_FORMATTER =
      DateTimeFormatter.ofPattern("HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

  public ForgotPasswordCompletionResult toResult(
      TrpResetPasswordApimEnvelope envelope, String expectedCustomerId) {
    Objects.requireNonNull(expectedCustomerId, "expectedCustomerId must not be null");
    if (envelope == null || envelope.errMessage() == null) {
      return contractFailure();
    }
    if (!envelope.errMessage().isEmpty()) {
      if (PASSWORD_INVALID.equals(envelope.errMessage())
          || PASSWORD_REUSED.equals(envelope.errMessage())
          || MEMBER_NOT_FOUND.equals(envelope.errMessage())) {
        return ForgotPasswordCompletionResult.rejected(envelope.errMessage());
      }
      return contractFailure();
    }
    List<TrpResetPasswordApimDataItem> data = envelope.data();
    if (data == null || data.size() != 1) {
      return contractFailure();
    }
    TrpResetPasswordApimDataItem item = data.get(0);
    if (!Boolean.TRUE.equals(item.success())) {
      return contractFailure();
    }
    if (!IdentifierNormalizer.equalsNormalized(expectedCustomerId, item.customerId())) {
      return contractFailure();
    }
    if (!isValidDate(item.updateDate()) || !isValidTime(item.updateTime())) {
      return contractFailure();
    }

    return ForgotPasswordCompletionResult.reset(item.updateDate(), item.updateTime());
  }

  private ForgotPasswordCompletionResult contractFailure() {
    return ForgotPasswordCompletionResult.technicalFailure(
        FailureOrigin.DOWNSTREAM_CONTRACT, "apim-reset-response");
  }

  private boolean isValidDate(String value) {
    try {
      LocalDate.parse(value, DATE_FORMATTER);
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private boolean isValidTime(String value) {
    try {
      LocalTime.parse(value, TIME_FORMATTER);
      return true;
    } catch (RuntimeException exception) {
      return false;
    }
  }
}

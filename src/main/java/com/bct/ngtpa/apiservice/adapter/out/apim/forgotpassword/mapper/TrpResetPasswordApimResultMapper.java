package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.TrpResetPasswordApimEnvelope;
import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.dto.TrpResetPasswordApimDataItem;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionResult;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Objects;

public final class TrpResetPasswordApimResultMapper {
    private static final String PASSWORD_INVALID = "err.tpa.password.invalid";
    private static final String MEMBER_NOT_FOUND = "err.tpa.member.not.found";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);

    public ForgotPasswordCompletionResult toResult(TrpResetPasswordApimEnvelope envelope, String expectedCustomerId) {
        Objects.requireNonNull(expectedCustomerId, "expectedCustomerId must not be null");
        if (envelope == null || envelope.errMessage() == null) {
            return ForgotPasswordCompletionResult.technicalFailure();
        }
        if (!envelope.errMessage().isEmpty()) {
            if (PASSWORD_INVALID.equals(envelope.errMessage()) || MEMBER_NOT_FOUND.equals(envelope.errMessage())) {
                return ForgotPasswordCompletionResult.rejected(envelope.errMessage());
            }
            return ForgotPasswordCompletionResult.technicalFailure();
        }
        List<TrpResetPasswordApimDataItem> data = envelope.data();
        if (data == null || data.size() != 1) {
            return ForgotPasswordCompletionResult.technicalFailure();
        }
        TrpResetPasswordApimDataItem item = data.get(0);
        if (!Boolean.TRUE.equals(item.success())) {
            return ForgotPasswordCompletionResult.technicalFailure();
        }
        if (!expectedCustomerId.equals(item.customerId())) {
            return ForgotPasswordCompletionResult.technicalFailure();
        }
        if (!isValidDate(item.updateDate()) || !isValidTime(item.updateTime())) {
            return ForgotPasswordCompletionResult.technicalFailure();
        }
        return ForgotPasswordCompletionResult.reset(item.updateDate(), item.updateTime());
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
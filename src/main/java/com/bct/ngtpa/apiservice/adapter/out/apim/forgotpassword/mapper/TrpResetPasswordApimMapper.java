package com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.mapper;

import com.bct.ngtpa.apiservice.adapter.out.apim.forgotpassword.dto.TrpResetPasswordApimRequest;
import com.bct.ngtpa.apiservice.application.usecase.auth.forgotpassword.complete.ForgotPasswordCompletionCommand;
import java.util.Objects;

public final class TrpResetPasswordApimMapper {
    public TrpResetPasswordApimRequest toRequest(ForgotPasswordCompletionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return new TrpResetPasswordApimRequest(command.customerId(), command.password());
    }
}
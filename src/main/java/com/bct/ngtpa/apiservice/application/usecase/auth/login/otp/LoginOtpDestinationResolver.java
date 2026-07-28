package com.bct.ngtpa.apiservice.application.usecase.auth.login.otp;

import com.bct.ngtpa.apiservice.application.dto.auth.otp.OtpChannel;
import com.bct.ngtpa.apiservice.application.dto.auth.login.preauth.*;
import java.util.*;

/**
 * Resolves an FE-supplied opaque ID only against the decrypted protected login
 * context.
 */
public final class LoginOtpDestinationResolver {
    public ResolvedLoginOtpDestination resolve(LoginPreAuthContext context, SendLoginOtpCommand command) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(command, "command must not be null");
        LoginPreAuthDestination destination = context.destinationsById().get(command.destinationId());
        if (destination == null || !command.destinationId().equals(destination.destinationId()))
            throw new InvalidLoginOtpDestinationException();
        return new ResolvedLoginOtpDestination(destination.destinationId(), channel(destination.channel()),
                destination.destination(), destination.maskedDestination());
    }

    private OtpChannel channel(String value) {
        if (value == null)
            throw new InvalidLoginOtpDestinationException();
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "email" -> OtpChannel.EMAIL;
            case "mobile", "sms" -> OtpChannel.SMS;
            default -> throw new InvalidLoginOtpDestinationException();
        };
    }
}

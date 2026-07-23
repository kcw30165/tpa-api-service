package com.bct.ngtpa.apiservice.application.dto.auth.login;

/**
 * A selectable OTP destination. destinationRef is opaque; maskedDestination is
 * display-only and the original destination remains protected downstream.
 */
public record OtpDestination(
        String destinationRef,
        OtpDestinationChannel channel,
        String maskedDestination) {
}

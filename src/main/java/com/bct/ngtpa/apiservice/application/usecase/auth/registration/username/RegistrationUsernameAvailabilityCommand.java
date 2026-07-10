package com.bct.ngtpa.apiservice.application.usecase.auth.registration.username;

import com.bct.ngtpa.apiservice.application.dto.auth.registration.RegistrationSelectedTrust;

public record RegistrationUsernameAvailabilityCommand(
        RegistrationSelectedTrust selectedTrust,
        String username,
        String correlationId) {
    @Override
    public String toString() {
        return "RegistrationUsernameAvailabilityCommand[selectedTrust=" + selectedTrust
                + ", username=<redacted>, correlationId=" + correlationId + "]";
    }
}

package com.bct.ngtpa.apiservice.application.dto.auth.registration;

/**
 * Framework-free selected trust model for registration username/check.
 * The latest FE and session contract contains only trustCode and accountEnv.
 */
public record RegistrationSelectedTrust(
                String trustCode,
                String accountEnv) {
}

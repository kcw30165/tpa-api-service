package com.bct.ngtpa.apiservice.shared.error;

/**
 * Resolves a safe user-facing message for a business error code.
 */
public interface ErrorMessageResolver {

    String resolve(String errorCode, String locale, String env, String trustCode, String schemeType);
}
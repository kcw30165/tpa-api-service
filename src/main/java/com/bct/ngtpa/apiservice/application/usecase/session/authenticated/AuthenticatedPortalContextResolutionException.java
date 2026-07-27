package com.bct.ngtpa.apiservice.application.usecase.session.authenticated;

/**
 * Non-sensitive typed failure for unavailable authenticated selected-account
 * context.
 */
public final class AuthenticatedPortalContextResolutionException extends RuntimeException {
    public AuthenticatedPortalContextResolutionException() {
        super("Authenticated account context unavailable");
    }
}

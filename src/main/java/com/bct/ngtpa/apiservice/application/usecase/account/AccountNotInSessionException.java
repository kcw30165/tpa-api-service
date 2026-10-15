package com.bct.ngtpa.apiservice.application.usecase.account;

/**
 * Thrown when the authenticated session is valid but the requested target {@code accountRef} is not
 * present in that session's account list.
 *
 * <p>Deliberately distinct from {@code AuthenticatedPortalContextResolutionException}, which
 * remains reserved for a genuinely invalid/expired/not-found session. This separation lets the web
 * layer map "your session is fine, but you don't have access to that account" (422 business
 * rejection) differently from "you need to re-authenticate" (401).
 */
public final class AccountNotInSessionException extends RuntimeException {}

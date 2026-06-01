package com.bct.ngtpa.apiservice.application.exception;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;

/**
 * Thrown when a portal access context cannot be resolved for an incoming request.
 *
 * <p>The external error code is kept stable as {@code err.member.context.unavailable}
 * to preserve backward compatibility with existing API clients.
 */
public class PortalAccessContextResolutionException extends ApplicationException {

    public PortalAccessContextResolutionException(String message) {
        super(ErrorCodes.MEMBER_CONTEXT_UNAVAILABLE, message);
    }

    public PortalAccessContextResolutionException(String errorCode, String message) {
        super(errorCode, message);
    }
}

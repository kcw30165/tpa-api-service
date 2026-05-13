package com.bct.ngtpa.apiservice.application.exception;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;

public class MemberContextResolutionException extends ApplicationException {

    public MemberContextResolutionException(String message) {
        super(ErrorCodes.MEMBER_CONTEXT_UNAVAILABLE, message);
    }
}

package com.bct.ngtpa.apiservice.application.exception;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;

public class InvalidContributionRequestException extends ApplicationException {

    public InvalidContributionRequestException(String message) {
        super(ErrorCodes.CONTRIBUTION_REQUEST_INVALID, message);
    }
}
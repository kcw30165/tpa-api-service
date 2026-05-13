package com.bct.ngtpa.apiservice.application.exception;

import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;

public class InvalidNotificationRequestException extends ApplicationException {

    public InvalidNotificationRequestException(String message) {
        super(ErrorCodes.NOTIFICATION_REQUEST_INVALID, message);
    }
}
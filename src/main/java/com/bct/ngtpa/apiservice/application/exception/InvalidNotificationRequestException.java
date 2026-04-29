package com.bct.ngtpa.apiservice.application.exception;

public class InvalidNotificationRequestException extends RuntimeException {

    public InvalidNotificationRequestException(String message) {
        super(message);
    }
}
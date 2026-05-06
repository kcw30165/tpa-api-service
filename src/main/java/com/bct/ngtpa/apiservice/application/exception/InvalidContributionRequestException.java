package com.bct.ngtpa.apiservice.application.exception;

public class InvalidContributionRequestException extends RuntimeException {

    public InvalidContributionRequestException(String message) {
        super(message);
    }
}
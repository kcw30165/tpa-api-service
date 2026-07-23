package com.bct.ngtpa.apiservice.application.usecase.auth.login.preauth;

public final class LoginDestinationMasker {
    public String mask(String channel, String destination) {
        if ("email".equals(channel)) {
            int at = destination.indexOf('@');
            if (at <= 0) throw new IllegalStateException("Invalid login email destination");
            return destination.substring(0, 1) + "*".repeat(Math.max(4, at - 1)) + destination.substring(at);
        }
        if ("mobile".equals(channel)) {
            if (destination.length() < 4) throw new IllegalStateException("Invalid login mobile destination");
            return "*".repeat(destination.length() - 4) + destination.substring(destination.length() - 4);
        }
        throw new IllegalStateException("Unsupported login destination channel");
    }
}

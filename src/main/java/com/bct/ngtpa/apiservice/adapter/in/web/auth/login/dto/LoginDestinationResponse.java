package com.bct.ngtpa.apiservice.adapter.in.web.auth.login.dto;

public record LoginDestinationResponse(String destinationId, String channel, String maskedDestination) {
    @Override
    public String toString() {
        return "LoginDestinationResponse[destinationId=<redacted>, channel=" + channel + ", maskedDestination="
                + maskedDestination + "]";
    }
}

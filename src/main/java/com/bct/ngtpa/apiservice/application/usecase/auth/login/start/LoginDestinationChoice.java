package com.bct.ngtpa.apiservice.application.usecase.auth.login.start;
public record LoginDestinationChoice(String destinationId,String channel,String maskedDestination){@Override public String toString(){return "LoginDestinationChoice[destinationId=<redacted>, channel="+channel+", maskedDestination="+maskedDestination+"]";}}

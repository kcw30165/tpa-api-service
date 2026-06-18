package com.bct.ngtpa.apiservice.shared.web;

public final class RequestHeaderContextKeys {

    public static final String ACCOUNT_REF_HEADER = "Account-Ref";
    public static final String ACCEPT_LANGUAGE_HEADER = "Accept-Language";
    
    public static final String SESSION_ID_HEADER = "Session-Id";
public static final String ATTRIBUTE_KEY = RequestHeaderContext.class.getName() + ".attribute";
    public static final String CONTEXT_KEY = RequestHeaderContext.class.getName() + ".context";

    private RequestHeaderContextKeys() {
    }
}
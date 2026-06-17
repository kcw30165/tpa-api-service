package com.bct.ngtpa.apiservice.shared.web;

import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;

public final class PortalAccessContextKeys {

    public static final String ATTRIBUTE_KEY = PortalAccessContext.class.getName() + ".attribute";
    public static final String CONTEXT_KEY = PortalAccessContext.class.getName() + ".context";

    private PortalAccessContextKeys() {
    }
}

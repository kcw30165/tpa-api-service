package com.bct.ngtpa.apiservice.shared.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import org.junit.jupiter.api.Test;

class PortalAccessContextKeysTest {

    @Test
    void exposesStableAttributeAndReactorContextKeys() {
        assertEquals(PortalAccessContext.class.getName() + ".attribute", PortalAccessContextKeys.ATTRIBUTE_KEY);
        assertEquals(PortalAccessContext.class.getName() + ".context", PortalAccessContextKeys.CONTEXT_KEY);
    }

    @Test
    void keysDoNotCollideWithRequestHeaderContextKeys() {
        assertEquals(false, PortalAccessContextKeys.ATTRIBUTE_KEY.equals(RequestHeaderContextKeys.ATTRIBUTE_KEY));
        assertEquals(false, PortalAccessContextKeys.CONTEXT_KEY.equals(RequestHeaderContextKeys.CONTEXT_KEY));
    }
}

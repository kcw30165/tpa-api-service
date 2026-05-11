package com.bct.ngtpa.apiservice.adapter.out.apim.credential;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultApimCredentialProfileResolverTest {

    @Test
    void returnsLegacyDefaultWhenNoProfilesConfigured() {
        ApimProperties props = new ApimProperties();
        DefaultApimCredentialProfileResolver resolver = new DefaultApimCredentialProfileResolver(props);

        ApimCredentialProfile profile = resolver.resolve(new ApimCredentialResolutionContext(null, null, null, null, null, null, null, null, null)).block();

        assertEquals(props.getOauth().getClientId(), profile.clientId());
        assertEquals(props.getEncryption().getApiKey(), profile.apiKey());
    }

    @Test
    void returnsConfiguredDefaultProfileWhenPresent() {
        ApimProperties props = new ApimProperties();
        ApimProperties.CredentialProfiles.Profile p = new ApimProperties.CredentialProfiles.Profile();
        p.setProfileId("p1");
        p.setClientId("cid");
        p.setClientSecret("csecret");
        p.setApiKey("akey");
        props.getCredentialProfiles().setProfiles(List.of(p));
        props.getCredentialProfiles().setDefaultProfileId("p1");

        DefaultApimCredentialProfileResolver resolver = new DefaultApimCredentialProfileResolver(props);

        ApimCredentialProfile profile = resolver.resolve(new ApimCredentialResolutionContext(null, null, null, null, null, null, null, null, null)).block();

        assertEquals("p1", profile.profileId());
        assertEquals("cid", profile.clientId());
        assertEquals("akey", profile.apiKey());
    }

    @Test
    void returnsLegacyDefaultWhenProfilesCollectionIsNull() {
        ApimProperties props = new ApimProperties();
        props.getCredentialProfiles().setProfiles(null);

        DefaultApimCredentialProfileResolver resolver = new DefaultApimCredentialProfileResolver(props);

        ApimCredentialProfile profile = resolver.resolve(new ApimCredentialResolutionContext(null, null, null, null, null, null, null, null, null)).block();

        assertEquals(props.getCredentialProfiles().getDefaultProfileId(), profile.profileId());
        assertEquals(props.getOauth().getClientId(), profile.clientId());
    }

    @Test
    void fallsBackToFirstConfiguredProfileWhenDefaultIsMissing() {
        ApimProperties props = new ApimProperties();
        ApimProperties.CredentialProfiles.Profile first = new ApimProperties.CredentialProfiles.Profile();
        first.setProfileId(null);
        first.setClientId("cid-first");
        first.setApiKey("akey-first");

        ApimProperties.CredentialProfiles.Profile second = new ApimProperties.CredentialProfiles.Profile();
        second.setProfileId("p2");
        second.setClientId("cid-second");
        second.setApiKey("akey-second");

        props.getCredentialProfiles().setProfiles(List.of(first, second));
        props.getCredentialProfiles().setDefaultProfileId("missing");

        DefaultApimCredentialProfileResolver resolver = new DefaultApimCredentialProfileResolver(props);

        ApimCredentialProfile profile = resolver.resolve(new ApimCredentialResolutionContext(null, null, null, null, null, null, null, null, null)).block();

        assertEquals(null, profile.profileId());
        assertEquals("cid-first", profile.clientId());
        assertEquals("akey-first", profile.apiKey());
    }
}

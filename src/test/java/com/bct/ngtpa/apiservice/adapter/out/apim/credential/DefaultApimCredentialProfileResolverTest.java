package com.bct.ngtpa.apiservice.adapter.out.apim.credential;

import com.bct.ngtpa.apiservice.adapter.out.apim.config.ApimProperties;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import com.bct.ngtpa.apiservice.application.dto.ActorUserRole;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultApimCredentialProfileResolverTest {

    @Test
    void nonAccountMissingProfileFails() {
        ApimProperties props = new ApimProperties();
        // no routing.nonAccountProfileId and no profiles configured -> resolution must fail
        DefaultApimCredentialProfileResolver resolver = new DefaultApimCredentialProfileResolver(props);

        StepVerifier.create(resolver.resolve(ApimDatabaseScope.NON_ACCOUNT, new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, null)))
                .expectErrorMatches(ex -> ex instanceof com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException)
                .verify();
    }

    @Test
    void resolvesConfiguredNonAccountProfileWhenPresent() {
        ApimProperties props = new ApimProperties();
        ApimProperties.CredentialProfiles.Profile p = new ApimProperties.CredentialProfiles.Profile();
        p.setProfileId("p1");
        p.setClientId("cid");
        p.setClientSecret("csecret");
        p.setApiKey("akey");
        props.getCredentialProfiles().setProfiles(List.of(p));
        // configure routing to reference the profile
        props.getRouting().setNonAccountProfileId("p1");

        DefaultApimCredentialProfileResolver resolver = new DefaultApimCredentialProfileResolver(props);

        ApimCredentialProfile profile = resolver.resolve(ApimDatabaseScope.NON_ACCOUNT, new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, null)).block();

        assertEquals("p1", profile.profileId());
        assertEquals("cid", profile.clientId());
        assertEquals("akey", profile.apiKey());
    }

    @Test
    void missingProfilesCollectionStillFailsResolution() {
        ApimProperties props = new ApimProperties();
        props.getCredentialProfiles().setProfiles(null);
        props.getRouting().setNonAccountProfileId("p1");

        DefaultApimCredentialProfileResolver resolver = new DefaultApimCredentialProfileResolver(props);

        StepVerifier.create(resolver.resolve(ApimDatabaseScope.NON_ACCOUNT, new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, null)))
                .expectErrorMatches(ex -> ex instanceof com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException)
                .verify();
    }

    @Test
    void unmappedAccountEnvFailsForAccountScope() {
        ApimProperties props = new ApimProperties();
        ApimProperties.CredentialProfiles.Profile p = new ApimProperties.CredentialProfiles.Profile();
        p.setProfileId("p2");
        p.setClientId("cid-second");
        p.setApiKey("akey-second");
        props.getCredentialProfiles().setProfiles(List.of(p));
        // no mapping for JR env -> resolution should fail for ACCOUNT_ENV scope

        DefaultApimCredentialProfileResolver resolver = new DefaultApimCredentialProfileResolver(props);

        StepVerifier.create(resolver.resolve(ApimDatabaseScope.ACCOUNT_ENV, new ApimCredentialResolutionContext(ActorUserRole.ROLE_USER_MBR, "JR")))
                .expectErrorMatches(ex -> ex instanceof com.bct.ngtpa.apiservice.exception.ApimProfileResolutionException)
                .verify();
    }
}

package com.bct.ngtpa.apiservice.adapter.out.security;

import com.bct.ngtpa.apiservice.application.dto.MemberContext;
import com.bct.ngtpa.apiservice.application.dto.MemberContextPurpose;
import com.bct.ngtpa.apiservice.application.exception.MemberContextResolutionException;
import com.bct.ngtpa.apiservice.adapter.out.security.TemporaryMemberContextProperties;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TemporaryMemberContextAdapterTest {

    @Test
    void resolvesNotificationsProfile() {
        var adapter = adapterWithBothProfiles();

        StepVerifier.create(adapter.resolveMemberContext(MemberContextPurpose.NOTIFICATIONS))
                .assertNext(ctx -> {
                    assertEquals("policyNo_for_notifications", ctx.policyNo());
                    assertEquals("certNo_for_notifications", ctx.certNo());
                    assertEquals("userId_for_notifications", ctx.userId());
                    assertEquals("trustCode_for_notifications", ctx.trustCode());
                    assertEquals("schemeType_for_notifications", ctx.schemeType());
                })
                .verifyComplete();
    }

    @Test
    void resolvesContributionsProfile() {
        var adapter = adapterWithBothProfiles();

        StepVerifier.create(adapter.resolveMemberContext(MemberContextPurpose.CONTRIBUTIONS))
                .assertNext(ctx -> {
                    assertEquals("policyNo_for_contributions", ctx.policyNo());
                    assertEquals("certNo_for_contributions", ctx.certNo());
                    assertEquals("userId_for_contributions", ctx.userId());
                    assertEquals("trustCode_for_contributions", ctx.trustCode());
                    assertEquals("schemeType_for_contributions", ctx.schemeType());
                })
                .verifyComplete();
    }

    @Test
    void failsWithClearExceptionWhenProfileIsMissing() {
        var properties = new TemporaryMemberContextProperties();
        properties.setProfiles(Map.of());
        var adapter = new TemporaryMemberContextAdapter(properties);

        StepVerifier.create(adapter.resolveMemberContext(MemberContextPurpose.NOTIFICATIONS))
                .expectErrorSatisfies(ex -> {
                    assertInstanceOf(MemberContextResolutionException.class, ex);
                    assertEquals(
                            "No temporary member context profile configured for purpose: NOTIFICATIONS",
                            ex.getMessage());
                })
                .verify();
    }

    @Test
    void blankOptionalFieldsDefaultToEmptyString() {
        var profile = new TemporaryMemberContextProperties.Profile();
        profile.setPolicyNo("policyNo");
        profile.setCertNo("certNo");
        profile.setUserId("userId");
        // trustCode and schemeType intentionally left as defaults

        var properties = new TemporaryMemberContextProperties();
        properties.setProfiles(Map.of("notifications", profile));
        var adapter = new TemporaryMemberContextAdapter(properties);

        StepVerifier.create(adapter.resolveMemberContext(MemberContextPurpose.NOTIFICATIONS))
                .assertNext(ctx -> {
                    assertEquals("", ctx.trustCode());
                    assertEquals("", ctx.schemeType());
                })
                .verifyComplete();
    }

    private static TemporaryMemberContextAdapter adapterWithBothProfiles() {
        var notifProfile = new TemporaryMemberContextProperties.Profile();
        notifProfile.setPolicyNo("policyNo_for_notifications");
        notifProfile.setCertNo("certNo_for_notifications");
        notifProfile.setUserId("userId_for_notifications");
        notifProfile.setTrustCode("trustCode_for_notifications");
        notifProfile.setSchemeType("schemeType_for_notifications");

        var contribProfile = new TemporaryMemberContextProperties.Profile();
        contribProfile.setPolicyNo("policyNo_for_contributions");
        contribProfile.setCertNo("certNo_for_contributions");
        contribProfile.setUserId("userId_for_contributions");
        contribProfile.setTrustCode("trustCode_for_contributions");
        contribProfile.setSchemeType("schemeType_for_contributions");

        var profiles = new LinkedHashMap<String, TemporaryMemberContextProperties.Profile>();
        profiles.put("notifications", notifProfile);
        profiles.put("contributions", contribProfile);

        var properties = new TemporaryMemberContextProperties();
        properties.setProfiles(profiles);

        return new TemporaryMemberContextAdapter(properties);
    }
}

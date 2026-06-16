package com.bct.ngtpa.apiservice.application.usecase;

import com.bct.ngtpa.apiservice.application.dto.AccountContext;
import com.bct.ngtpa.apiservice.application.dto.ActorContext;
import com.bct.ngtpa.apiservice.application.dto.PortalAccessContext;
import com.bct.ngtpa.apiservice.application.dto.TermStatus;
import com.bct.ngtpa.apiservice.application.exception.InvalidContributionRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContributionSummarySupportTest {

    // -------------------------------------------------------------------------
    // newFetchCommand — field mapping from PortalAccessContext
    // -------------------------------------------------------------------------

    @Test
    void newFetchCommandMapsAccountEnvAndMbrTypeFromContext() {
        var ctx = contextWith("pol", "cert", "user", "trust", "scheme");

        var cmd = ContributionSummarySupport.newFetchCommand(
                "01/01/2026", "31/03/2026", ctx);

        assertEquals("JP", cmd.accountEnv());
        assertEquals("", cmd.mbrType());
        assertEquals("01/01/2026", cmd.coverFrom());
        assertEquals("31/03/2026", cmd.coverTo());
    }

    @Test
    void newFetchCommandMapsPolicyAndCertFromAccount() {
        var ctx = contextWith("policyNo_test", "certNo_test", "userId_test", "trust_test", "scheme_test");

        var cmd = ContributionSummarySupport.newFetchCommand(
                "01/01/2026", "31/03/2026", ctx);

        assertEquals("policyNo_test", cmd.policyNo());
        assertEquals("certNo_test", cmd.certNo());
    }

    @Test
    void newFetchCommandMapsUserIdFromActorNotFromAccount() {
        // actorUserId and memberOwner userId are intentionally different to assert
        // that userId is sourced from actor, not from account or memberOwner
        var ctx = new PortalAccessContext(
                new ActorContext("actor_user_id", "SELF"),
                new AccountContext("contributions", "JP",
                "policyNo", "certNo", "trust", "scheme", TermStatus.BLANK, null));

        var cmd = ContributionSummarySupport.newFetchCommand(
                "01/01/2026", "31/03/2026", ctx);

        assertEquals("actor_user_id", cmd.userId());
    }

    @Test
    void newFetchCommandMapsTrustCodeAndSchemeTypeFromAccount() {
        var ctx = contextWith("pol", "cert", "user", "trustCode_test", "schemeType_test");

        var cmd = ContributionSummarySupport.newFetchCommand(
                "01/01/2026", "31/03/2026", ctx);

        assertEquals("trustCode_test", cmd.trustCode());
        assertEquals("schemeType_test", cmd.schemeType());
    }

    // -------------------------------------------------------------------------
    // Date helpers
    // -------------------------------------------------------------------------

    @Test
    void formatDateProducesDdMmYyyyString() {
        assertEquals("31/03/2026", ContributionSummarySupport.formatDate(LocalDate.of(2026, 3, 31)));
    }

    @Test
    void parseRequiredDateAcceptsValidDdMmYyyy() {
        var date = ContributionSummarySupport.parseRequiredDate("01/01/2026", "fromDate");
        assertEquals(LocalDate.of(2026, 1, 1), date);
    }

    @Test
    void parseRequiredDateRejectsNullWithFieldName() {
        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> ContributionSummarySupport.parseRequiredDate(null, "fromDate"));
        assertEquals("fromDate must be provided in dd/MM/yyyy format", ex.getMessage());
    }

    @Test
    void parseRequiredDateRejectsWrongFormatWithFieldName() {
        var ex = assertThrows(InvalidContributionRequestException.class,
                () -> ContributionSummarySupport.parseRequiredDate("2026-01-01", "toDate"));
        assertEquals("toDate must be provided in dd/MM/yyyy format", ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static PortalAccessContext contextWith(
            String policyNo, String certNo, String actorUserId, String trustCode, String schemeType) {
        return new PortalAccessContext(
                new ActorContext(actorUserId, "SELF"),
            new AccountContext(
                "contributions", "JP", policyNo, certNo, trustCode, schemeType,
                TermStatus.BLANK, null));
    }
}

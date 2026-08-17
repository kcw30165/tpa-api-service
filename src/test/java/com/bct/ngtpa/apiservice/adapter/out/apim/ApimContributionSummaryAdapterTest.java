package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.junit.jupiter.api.Assertions.*;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.*;
import com.bct.ngtpa.apiservice.application.dto.*;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ApimContributionSummaryAdapterTest {
 private final ApimContributionSummaryAdapter adapter=new ApimContributionSummaryAdapter(null);
 @Test void mapsEnvelope(){var d=(com.bct.ngtpa.apiservice.domain.model.ContributionSummaryDataset)ReflectionTestUtils.invokeMethod(adapter,"toContributionSummaryDataset",sample());assertEquals("HKD",d.currency());assertEquals(2,d.entries().size());}
 @Test void payloadHasOnlyDownstreamFields() throws Exception {var c=new FetchContributionSummaryCommand("POL","27","USER","05/04/2026","05/05/2026","JP",ActorUserRole.ROLE_USER_MBR);var r=(GetContributionSummaryApimRequest)ReflectionTestUtils.invokeMethod(adapter,"toApimRequest",c);var j=new ObjectMapper().writeValueAsString(r);assertFalse(j.contains("accountEnv"));assertFalse(j.contains("actorUserRole"));assertFalse(j.contains("trustCode"));assertFalse(j.contains("schemeType"));assertEquals("27",r.getCertNo());}
 @Test void invalidEnvelopeRemainsInvalid(){var e=assertThrows(ApimException.class,()->ReflectionTestUtils.invokeMethod(adapter,"toContributionSummaryDataset",new Object[]{null}));assertEquals(ErrorCodes.APIM_RESPONSE_INVALID,e.getErrorCode());}
 private static ApimResponseEnvelope<GetContributionSummaryDataItem> sample(){return ApimResponseEnvelope.<GetContributionSummaryDataItem>builder().response(ApimResponseBody.<GetContributionSummaryDataItem>builder().errMessage("").data(List.of(GetContributionSummaryDataItem.builder().currency("HKD").dispSrc(List.of()).contDtl(List.of(GetContributionSummaryContDtlItem.builder().dispSrc("ER").coverFrom("01/03/2026").coverTo("31/03/2026").dealDate("01/03/2026").contAmt(new BigDecimal("1.00")).build(),GetContributionSummaryContDtlItem.builder().dispSrc("EE").coverFrom("01/03/2026").coverTo("31/03/2026").dealDate("01/03/2026").contAmt(new BigDecimal("2.00")).build())).build())).build()).build();}
}

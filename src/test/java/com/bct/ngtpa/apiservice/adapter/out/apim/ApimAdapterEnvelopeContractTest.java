package com.bct.ngtpa.apiservice.adapter.out.apim;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetContributionSummaryDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetCountryListDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMemberInfoDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.GetMessageBoardDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateMemberInfoApimDataItem;
import com.bct.ngtpa.apiservice.adapter.out.apim.dto.UpdateNotificationReadStatusApimDataItem;
import com.bct.ngtpa.apiservice.exception.ApimException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApimAdapterEnvelopeContractTest {

    @Test
    void contributionSummaryAdapterEnvelopeRejectsEmptyErrMessageWithoutDataArray() {
        assertMissingDataSuccessIsInvalid(GetContributionSummaryDataItem.class);
    }

    @Test
    void contributionSummaryAdapterEnvelopeAllowsEmptyDataArrayForNoRecords() {
        assertNoRecordsSuccessIsValid(GetContributionSummaryDataItem.class);
    }

    @Test
    void memberInfoAdapterEnvelopeRejectsEmptyErrMessageWithoutDataArray() {
        assertMissingDataSuccessIsInvalid(GetMemberInfoDataItem.class);
    }

    @Test
    void memberInfoAdapterEnvelopeAllowsEmptyDataArrayForNoRecords() {
        assertNoRecordsSuccessIsValid(GetMemberInfoDataItem.class);
    }

    @Test
    void noticeMessageAdapterEnvelopeRejectsEmptyErrMessageWithoutDataArray() {
        assertMissingDataSuccessIsInvalid(GetMessageBoardDataItem.class);
    }

    @Test
    void noticeMessageAdapterEnvelopeAllowsEmptyDataArrayForNoRecords() {
        assertNoRecordsSuccessIsValid(GetMessageBoardDataItem.class);
    }

    @Test
    void notificationReadStatusAdapterEnvelopeRejectsEmptyErrMessageWithoutDataArray() {
        assertMissingDataSuccessIsInvalid(UpdateNotificationReadStatusApimDataItem.class);
    }

    @Test
    void notificationReadStatusAdapterEnvelopeAllowsEmptyDataArrayForNoRecords() {
        assertNoRecordsSuccessIsValid(UpdateNotificationReadStatusApimDataItem.class);
    }

    @Test
    void referenceDataCountriesAdapterEnvelopeRejectsEmptyErrMessageWithoutDataArray() {
        assertMissingDataSuccessIsInvalid(GetCountryListDataItem.class);
    }

    @Test
    void referenceDataCountriesAdapterEnvelopeAllowsEmptyDataArrayForNoRecords() {
        assertNoRecordsSuccessIsValid(GetCountryListDataItem.class);
    }

    @Test
    void updatePersonalInformationAdapterEnvelopeRejectsEmptyErrMessageWithoutDataArray() {
        assertMissingDataSuccessIsInvalid(UpdateMemberInfoApimDataItem.class);
    }

    @Test
    void updatePersonalInformationAdapterEnvelopeAllowsEmptyDataArrayForNoRecords() {
        assertNoRecordsSuccessIsValid(UpdateMemberInfoApimDataItem.class);
    }

    private static <T> void assertMissingDataSuccessIsInvalid(Class<T> ignoredPayloadType) {
        ApimException exception = assertThrows(ApimException.class,
                () -> ApimResponseValidator.requireSuccessData(ApimEnvelopeFixtures.missingDataSuccess()));

        assertEquals(ErrorCodes.APIM_RESPONSE_INVALID, exception.getErrorCode());
        assertEquals("APIM response payload is invalid: response.data is required when err-message is empty.",
                exception.getMessage());
    }

    private static <T> void assertNoRecordsSuccessIsValid(Class<T> ignoredPayloadType) {
        List<T> data = assertDoesNotThrow(
                () -> ApimResponseValidator.requireSuccessData(ApimEnvelopeFixtures.noRecords()));

        assertEquals(List.of(), data);
    }
}


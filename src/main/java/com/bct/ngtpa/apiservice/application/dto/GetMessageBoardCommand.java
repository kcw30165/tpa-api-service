package com.bct.ngtpa.apiservice.application.dto;

import com.bct.ngtpa.apiservice.adapter.in.web.request.GetMessageBoardWebRequest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record GetMessageBoardCommand(
        String policyNo,
        String certNo,
        String userId,
        String refDate,
        String env,
        String mbrType
) {
    private static final DateTimeFormatter REF_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static GetMessageBoardCommand from(GetMessageBoardWebRequest request) {
        String refDate = (request.refDate() != null && !request.refDate().isBlank())
                ? request.refDate()
                : LocalDate.now().format(REF_DATE_FORMATTER);
        return new GetMessageBoardCommand(
                request.policyNo(),
                request.certNo(),
                request.userId(),
                refDate,
                request.env(),
                request.mbrType()
        );
    }
}

package com.bct.ngtpa.apiservice.application.dto.fundfactsheet;

/**
 * One normalized fund fact sheet row. Names are carried exactly as received (a null or blank value
 * stays as it is). A link is null when it was absent, blank or malformed; a valid link is carried
 * verbatim.
 */
public record FundFactSheetRowResult(
    int seq, String nameEng, String nameChi, String linkEng, String linkChi) {}

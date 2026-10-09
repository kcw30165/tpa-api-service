package com.bct.ngtpa.apiservice.application.dto.fundfactsheet;

/**
 * One fund fact sheet row exactly as the downstream system supplied it. {@code seq} is nullable so
 * that a missing value stays detectable; names and links are not interpreted here.
 */
public record RawFundFactSheetRow(
    Integer seq, String nameEng, String nameChi, String linkEng, String linkChi) {}

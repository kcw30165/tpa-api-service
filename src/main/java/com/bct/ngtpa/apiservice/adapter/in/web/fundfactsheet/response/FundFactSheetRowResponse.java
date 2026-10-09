package com.bct.ngtpa.apiservice.adapter.in.web.fundfactsheet.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One fund fact sheet row of the public response.
 *
 * <p>Only {@code factsheetUrl} is omitted from the JSON when it is null. There is deliberately no
 * class-level inclusion rule: {@code fundNameZh} must stay in the JSON as {@code null} so that the
 * front end can tell "no Chinese name" from "field missing".
 */
public record FundFactSheetRowResponse(
    int seq,
    String fundName,
    String fundNameEn,
    String fundNameZh,
    @JsonInclude(JsonInclude.Include.NON_NULL) String factsheetUrl) {}

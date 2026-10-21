package com.bct.ngtpa.apiservice.adapter.in.web.response;

/** The data block of the GET personal-information response: the page header and the form schema. */
public record PersonalInformationDataResponse(PageResponse page, FormSchemaResponse form) {}

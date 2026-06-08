package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.List;

public interface ApiResponse {
    boolean success();

    ApiStatus status();

    List<ApiMessage> messages();

    List<ApiError> errors();
}
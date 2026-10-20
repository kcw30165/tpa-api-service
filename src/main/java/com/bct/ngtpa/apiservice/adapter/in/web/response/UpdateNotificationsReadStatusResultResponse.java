package com.bct.ngtpa.apiservice.adapter.in.web.response;

import java.util.Objects;

/**
 * Wraps {@link UpdateNotificationsReadStatusResponse} under a {@code data} field, so that the
 * public body is {@code {success, status, result: {data: {notifications}}, messages, errors}}.
 */
public record UpdateNotificationsReadStatusResultResponse(
    UpdateNotificationsReadStatusResponse data) {

  public UpdateNotificationsReadStatusResultResponse {
    Objects.requireNonNull(data, "data must not be null");
  }
}

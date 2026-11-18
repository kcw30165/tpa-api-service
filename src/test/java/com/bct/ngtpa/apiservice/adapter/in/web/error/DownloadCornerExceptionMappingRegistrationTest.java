package com.bct.ngtpa.apiservice.adapter.in.web.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.bct.ngtpa.apiservice.adapter.in.web.response.ApiStatus;
import com.bct.ngtpa.apiservice.application.exception.DownloadCornerRetrievalException;
import com.bct.ngtpa.apiservice.shared.error.ErrorCodes;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DownloadCornerExceptionMappingRegistrationTest {

  @Test
  void registersDownloadCornerRetrievalAsServiceUnavailable() {
    var registry = new ExceptionMappingRegistryConfig().exceptionMappingRegistry();
    var exception = DownloadCornerRetrievalException.systemFailure();

    ErrorDescriptor descriptor =
        registry.resolve(exception, new ErrorContext("GET", "/api/v1/download-corner", "req-1"));

    assertThat(descriptor.httpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(descriptor.apiStatus()).isEqualTo(ApiStatus.SYSTEM_ERROR);
    assertThat(descriptor.errors())
        .containsExactly(
            new ErrorEntry(
                "SYSTEM",
                ErrorCodes.DOWNLOAD_CORNER_RETRIEVAL_UNAVAILABLE,
                List.of(),
                MessagePolicy.NONE));
    assertThat(registry.diagnosticMessage(exception)).isEqualTo("Download Corner retrieval failed");
  }
}

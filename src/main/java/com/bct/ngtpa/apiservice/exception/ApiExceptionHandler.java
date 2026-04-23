package com.bct.ngtpa.apiservice.exception;

import com.bct.ngtpa.apiservice.dto.ApiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApimException.class)
    public ResponseEntity<ApiErrorResponse> handleApimException(ApimException ex) {
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(new ApiErrorResponse(ex.getMessage()));
    }
}

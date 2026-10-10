package com.itsm.backend.common.error;

import org.springframework.http.HttpStatus;

public class PayloadTooLargeException extends ApiException {

    public PayloadTooLargeException(String code, String message) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, code, message);
    }
}

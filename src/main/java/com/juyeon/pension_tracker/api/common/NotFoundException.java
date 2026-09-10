package com.juyeon.pension_tracker.api.common;

/**
 * 요청한 리소스를 찾을 수 없을 때 발생하는 예외.
 * GlobalExceptionHandler에서 404로 변환된다.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}

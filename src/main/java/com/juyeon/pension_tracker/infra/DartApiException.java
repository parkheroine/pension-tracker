package com.juyeon.pension_tracker.infra;

/**
 * DART API 호출 실패 시 발생하는 예외.
 * status가 "000"(정상)이 아닌 경우 또는 네트워크 오류 시 발생한다.
 */
public class DartApiException extends RuntimeException {

    public DartApiException(String message) {
        super(message);
    }

    public DartApiException(String message, Throwable cause) {
        super(message, cause);
    }
}

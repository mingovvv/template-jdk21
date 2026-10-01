package com.example.skeleton.common.error;

/**
 * 특정 기능에 속하지 않는 공통 에러 코드.
 * 기능별 코드는 각 기능 패키지의 enum이 소유함.
 * 상태 코드와의 매핑은 {@code GlobalExceptionHandler}의 각 핸들러가 정함.
 *
 * AUTH_ 계열(401)과 ACCESS_DENIED(403)는 Security 필터 체인에서 발생함.
 * {@code SecurityProblemDetailHandler}가 {@code GlobalExceptionHandler}로 넘겨 응답을 만듦.
 */
enum CommonErrorCode implements ErrorCode {

    // 요청 형식 오류 (400) / 없는 경로 (404) / 지원하지 않는 메서드 (405)
    VALIDATION_FAILED, MALFORMED_REQUEST, INVALID_PARAMETER, MISSING_PARAMETER, METHOD_NOT_ALLOWED, ENDPOINT_NOT_FOUND,

    // 지원하지 않는 Content-Type (415) / 만들 수 없는 응답 형식 (406) / 업로드 크기 초과 (413)
    UNSUPPORTED_MEDIA_TYPE, NOT_ACCEPTABLE, PAYLOAD_TOO_LARGE,

    // 전용 코드가 없는 4xx (방화벽 거부, 필수 헤더·쿠키 누락 등). 구체 사유는 상태 코드가 말함.
    REQUEST_REJECTED,

    // 인증 실패 (401) / 권한 부족 (403)
    AUTH_REQUIRED, AUTH_TOKEN_EXPIRED, AUTH_TOKEN_INVALID, ACCESS_DENIED,

    // 서버 결함 (500) / 일시적 처리 불가 (503)
    INTERNAL_ERROR, SERVICE_UNAVAILABLE,

    // 외부 호출 실패. 상대가 정상 응답하지 않음 (502) / 시간 안에 응답하지 않음 (504)
    UPSTREAM_ERROR, UPSTREAM_TIMEOUT
}

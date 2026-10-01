package com.example.skeleton.common.error;

/**
 * 에러 코드 계약.
 * 구현은 각 기능 패키지의 enum이 담당함 (예: {@code order} 패키지의 {@code OrderErrorCode}).
 * 계약은 common이, 코드 목록은 기능이 소유함.
 *
 * 상수명은 {@code <기능접두어>_<의미>} 형식의 SCREAMING_SNAKE_CASE임 (예: {@code ORDER_NOT_FOUND}).
 * 일련번호 체계는 쓰지 않음. 이름이 곧 문서임.
 *
 * 상수명이 그대로 응답의 {@code code} 값이 됨. 문자열 리터럴을 따로 두지 않음.
 *
 * 코드마다 {@code error.<코드>} 키로 {@code messages*.properties} 모든 번들에 메시지를 등록함.
 * 누락은 {@code ErrorCodeMessageBundleTest}가 빌드 실패로 잡음.
 */
public interface ErrorCode {

    /** 클라이언트가 분기하는 에러 코드. enum 상수명이 그대로 쓰임. */
    default String code() {
        return ((Enum<?>) this).name();
    }

    /** detail 메시지를 찾는 번들 키 ({@code error.<코드>}). */
    default String messageKey() {
        return "error.%s".formatted(code());
    }
}

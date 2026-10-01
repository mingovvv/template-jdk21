package com.example.skeleton.order.domain.error;

import com.example.skeleton.common.error.ErrorCode;

/** order 기능이 소유하는 에러 코드. 상수를 추가하면 messages*.properties 모든 번들에 메시지도 등록함. */
public enum OrderErrorCode implements ErrorCode {
    // 조회/상태
    ORDER_NOT_FOUND, INVALID_ORDER_STATUS,

    // 채널 주문 등록
    ORDER_DUPLICATE, ORDER_STORE_CLOSED, ORDER_OUT_OF_DELIVERY_AREA, ORDER_MENU_UNAVAILABLE, ORDER_PRICE_MISMATCH,
    ORDER_MIN_AMOUNT_NOT_MET,

    // 채널 주문 취소
    ORDER_CANCEL_WINDOW_EXPIRED
}

package com.example.skeleton.order.domain.error;

import com.example.skeleton.common.error.ConflictException;

/**
 * 같은 채널 주문번호가 이미 등록되어 있음 (채널사의 재시도·중복 전송).
 * 정책 위반과 달리 멱등 처리(기존 주문 반환 등)라는 별도 행동의 후보라서 독립 클래스로 둠.
 */
public class DuplicateOrderException extends ConflictException {

    public DuplicateOrderException(String channel, String channelOrderId) {
        super(OrderErrorCode.ORDER_DUPLICATE,
                "Order already registered from channel %s: %s".formatted(channel, channelOrderId),
                channel,
                channelOrderId);
    }
}

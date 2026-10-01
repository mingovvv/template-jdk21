package com.example.skeleton.order.domain.error;

import com.example.skeleton.common.error.UnprocessableException;

/**
 * 주문 정책 위반.
 * 응답: 422 + 위반한 정책의 에러 코드.
 * 형식은 유효하나 비즈니스 규칙상 접수·취소할 수 없는 요청임.
 *
 * 규칙마다 예외 클래스를 만들지 않음.
 * 영업 종료, 배달권역 이탈, 품절, 금액 불일치 등은 서버의 행동이 전부 "거부 후 422"로 동일하므로 클래스는 하나면 충분함.
 * 규칙의 구분은 {@link OrderErrorCode}가 담당함. 클래스는 서버 행동 단위, 코드는 클라이언트 식별 단위임.
 */
public class OrderPolicyException extends UnprocessableException {

    public OrderPolicyException(OrderErrorCode errorCode, String logMessage, Object... messageArgs) {
        super(errorCode, logMessage, messageArgs);
    }
}

package com.example.skeleton.common.error;

/**
 * "요청이 리소스의 현재 상태와 충돌함" 계열 에러의 베이스 타입.
 * 응답: 409 + 기능 에러 코드.
 * 예: 이미 취소된 주문을 다시 취소, 중복 주문 (INVALID_ORDER_STATUS, ORDER_DUPLICATE).
 *
 * 400은 요청 형식 오류, 409는 리소스의 현재 상태와 충돌, 422는 상태와 무관한 정책 위반임.
 *
 * 직접 던지지 않고 어드바이스가 상위 타입으로 잡기 위한 분류라서 추상임.
 * 던질 때는 반드시 이름 있는 기능 예외를 만들어 상속함.
 *
 * {@code logMessage}는 로그·스택트레이스용이라 영어 고정임.
 * 응답의 detail은 어드바이스가 {@link ErrorCode#messageKey()}와 {@code messageArgs}로 요청 로케일에 맞춰 따로 만듦.
 */
public abstract class ConflictException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] messageArgs;

    /**
     * errorCode는 응답의 code, logMessage는 로그용 영어 메시지임.
     * messageArgs는 번들 메시지의 {0}, {1} 자리에 순서대로 들어감.
     */
    protected ConflictException(ErrorCode errorCode, String logMessage, Object... messageArgs) {
        super(logMessage);
        this.errorCode = errorCode;
        this.messageArgs = messageArgs;
    }

    /** 응답의 code와 detail 메시지 키를 정하는 기능 에러 코드. */
    public ErrorCode errorCode() {
        return errorCode;
    }

    /** detail 메시지 치환 인자. 배열이라 밖에서 바꾸지 못하게 복사본을 돌려줌. */
    public Object[] messageArgs() {
        return messageArgs.clone();
    }
}

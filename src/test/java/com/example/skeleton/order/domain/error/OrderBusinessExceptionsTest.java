package com.example.skeleton.order.domain.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 주문 비즈니스 예외 카탈로그의 계약. 각 예외가 올바른 코드·메시지 인자·로그 메시지를 소유하는지 검증함. */
class OrderBusinessExceptionsTest {

    @Test
    void duplicateOrderCarriesChannelIdentity() {
        DuplicateOrderException e = new DuplicateOrderException("BAEMIN", "B123456");

        assertThat(e.errorCode().code()).isEqualTo("ORDER_DUPLICATE");
        assertThat(e.messageArgs()).containsExactly("BAEMIN", "B123456");
        assertThat(e.getMessage()).contains("BAEMIN").contains("B123456");
    }

    @Test
    void policyExceptionCarriesTheGivenRuleCode() {
        OrderPolicyException storeClosed = new OrderPolicyException(OrderErrorCode.ORDER_STORE_CLOSED,
                "Store 42 is closed", 42L);
        OrderPolicyException priceMismatch = new OrderPolicyException(
                OrderErrorCode.ORDER_PRICE_MISMATCH, "Total 10000 != calculated 12000", 10000, 12000);

        assertThat(storeClosed.errorCode().code()).isEqualTo("ORDER_STORE_CLOSED");
        assertThat(priceMismatch.errorCode().code()).isEqualTo("ORDER_PRICE_MISMATCH");
        assertThat(priceMismatch.messageArgs()).containsExactly(10000, 12000);
    }

    @Test
    void cancelWindowExpiryIsAPolicyViolationNotAStatusConflict() {
        UUID orderId = UUID.randomUUID();
        OrderPolicyException e = new OrderPolicyException(
                OrderErrorCode.ORDER_CANCEL_WINDOW_EXPIRED, "Order " + orderId + " passed the cancel window", orderId);

        assertThat(e.errorCode().code()).isEqualTo("ORDER_CANCEL_WINDOW_EXPIRED");
    }
}

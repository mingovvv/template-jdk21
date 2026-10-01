package com.example.skeleton.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import com.example.skeleton.order.domain.error.InvalidOrderStatusException;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void createStartsAsPending() {
        Order order = Order.create("keyboard", 2, NOW);

        assertThat(order.id()).isNotNull();
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.createdAt()).isEqualTo(NOW);
    }

    @Test
    void confirmMovesPendingToConfirmed() {
        Order confirmed = Order.create("keyboard", 2, NOW).confirm();

        assertThat(confirmed.status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void confirmRejectsNonPendingOrder() {
        Order confirmed = Order.create("keyboard", 2, NOW).confirm();

        assertThatThrownBy(confirmed::confirm)
                .isInstanceOf(InvalidOrderStatusException.class)
                .hasMessageContaining("CONFIRMED");
    }

    @Test
    void cancelAllowsPendingAndConfirmed() {
        assertThat(Order.create("a", 1, NOW).cancel().status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(Order.create("b", 1, NOW).confirm().cancel().status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelRejectsAlreadyCancelledOrder() {
        Order cancelled = Order.create("keyboard", 2, NOW).cancel();

        assertThatThrownBy(cancelled::cancel).isInstanceOf(InvalidOrderStatusException.class);
    }
}

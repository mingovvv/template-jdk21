package com.example.skeleton.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import com.example.skeleton.order.domain.Order;
import com.example.skeleton.order.domain.OrderStatus;
import com.example.skeleton.order.domain.error.InvalidOrderStatusException;
import com.example.skeleton.order.domain.error.OrderNotFoundException;
import com.example.skeleton.order.storage.InMemoryOrderRepository;
import org.junit.jupiter.api.Test;

/** 서비스 테스트는 실제 인메모리 리포지토리와 고정 Clock으로 돌림. mock이 필요 없음. */
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final OrderService orderService = new OrderService(new InMemoryOrderRepository(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createPersistsPendingOrderWithClockTime() {
        Order order = orderService.create("keyboard", 2);

        assertThat(orderService.get(order.id())).isEqualTo(order);
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.createdAt()).isEqualTo(NOW);
    }

    @Test
    void getUnknownOrderThrowsNotFound() {
        assertThatThrownBy(() -> orderService.get(UUID.randomUUID())).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void confirmPersistsNewStatus() {
        Order order = orderService.create("keyboard", 2);

        orderService.confirm(order.id());

        assertThat(orderService.get(order.id()).status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void cancelCancelledOrderThrowsConflict() {
        Order order = orderService.create("keyboard", 2);
        orderService.cancel(order.id());

        assertThatThrownBy(() -> orderService.cancel(order.id())).isInstanceOf(InvalidOrderStatusException.class);
    }

    @Test
    void listReturnsAllOrders() {
        orderService.create("keyboard", 2);
        orderService.create("mouse", 1);

        assertThat(orderService.list()).hasSize(2);
    }

    @Test
    void deleteRemovesOrder() {
        Order order = orderService.create("keyboard", 2);

        orderService.delete(order.id());

        assertThatThrownBy(() -> orderService.get(order.id())).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void deleteUnknownOrderThrowsNotFound() {
        assertThatThrownBy(() -> orderService.delete(UUID.randomUUID())).isInstanceOf(OrderNotFoundException.class);
    }
}

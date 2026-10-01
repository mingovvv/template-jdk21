package com.example.skeleton.order.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import com.example.skeleton.order.domain.error.InvalidOrderStatusException;

/**
 * 불변 도메인 모델.
 * 상태 전이는 새 인스턴스를 반환하고 불법 전이를 거부하므로, 서비스 레이어가 어떻게 호출하든 주문은 잘못된 상태에 도달할 수 없음.
 */
@Builder(toBuilder = true)
public record Order(
        UUID id,
        String productName,
        int quantity,
        OrderStatus status,
        Instant createdAt) {

    public static Order create(String productName, int quantity, Instant now) {
        return Order.builder()
                .id(UUID.randomUUID())
                .productName(productName)
                .quantity(quantity)
                .status(OrderStatus.PENDING)
                .createdAt(now)
                .build();
    }

    /** PENDING에서 CONFIRMED로 전이. */
    public Order confirm() {
        if (status != OrderStatus.PENDING) {
            throw new InvalidOrderStatusException(id, status, "confirm");
        }
        return withStatus(OrderStatus.CONFIRMED);
    }

    /** PENDING 또는 CONFIRMED에서 CANCELLED로 전이. */
    public Order cancel() {
        if (status == OrderStatus.CANCELLED) {
            throw new InvalidOrderStatusException(id, status, "cancel");
        }
        return withStatus(OrderStatus.CANCELLED);
    }

    private Order withStatus(OrderStatus newStatus) {
        return this.toBuilder().status(newStatus).build();
    }
}

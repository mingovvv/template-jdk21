package com.example.skeleton.order.api.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import com.example.skeleton.order.domain.Order;
import com.example.skeleton.order.domain.OrderStatus;

@Builder
public record OrderResponse(
        UUID id,
        String productName,
        int quantity,
        OrderStatus status,
        Instant createdAt) {

    public static OrderResponse from(Order order) {
        return OrderResponse.builder()
                .id(order.id())
                .productName(order.productName())
                .quantity(order.quantity())
                .status(order.status())
                .createdAt(order.createdAt())
                .build();
    }

}

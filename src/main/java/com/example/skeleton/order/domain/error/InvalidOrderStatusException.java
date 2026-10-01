package com.example.skeleton.order.domain.error;

import java.util.UUID;

import com.example.skeleton.common.error.ConflictException;
import com.example.skeleton.order.domain.OrderStatus;

public class InvalidOrderStatusException extends ConflictException {

    public InvalidOrderStatusException(UUID orderId, OrderStatus status, String action) {
        super(
            OrderErrorCode.INVALID_ORDER_STATUS,
            "Cannot %s order %s in status %s".formatted(action, orderId, status),
            orderId,
            status,
            action);
    }
}

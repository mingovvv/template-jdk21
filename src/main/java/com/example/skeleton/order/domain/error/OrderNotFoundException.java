package com.example.skeleton.order.domain.error;

import java.util.UUID;
import com.example.skeleton.common.error.ResourceNotFoundException;

public class OrderNotFoundException extends ResourceNotFoundException {

    public OrderNotFoundException(UUID orderId) {
        super(OrderErrorCode.ORDER_NOT_FOUND, "Order not found: %s".formatted(orderId), orderId);
    }
}

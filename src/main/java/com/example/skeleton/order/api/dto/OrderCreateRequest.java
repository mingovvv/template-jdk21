package com.example.skeleton.order.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record OrderCreateRequest(

        @NotBlank(message = "productName is required")
        String productName,

        @Positive(message = "quantity must be positive")
        int quantity

) {
}

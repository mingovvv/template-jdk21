package com.example.skeleton.order.api;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import com.example.skeleton.common.response.PageResponse;
import com.example.skeleton.order.api.dto.OrderCreateRequest;
import com.example.skeleton.order.api.dto.OrderResponse;
import com.example.skeleton.order.application.OrderService;
import com.example.skeleton.order.domain.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderCreateRequest request) {
        Order order = orderService.create(request.productName(), request.quantity());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(order.id())
                .toUri();
        return ResponseEntity.created(location).body(OrderResponse.from(order));
    }

    @GetMapping
    PageResponse<OrderResponse> list() {
        return PageResponse.all(
                orderService.list().stream().map(OrderResponse::from).toList());
    }

    @GetMapping("/{id}")
    OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(orderService.get(id));
    }

    @PostMapping("/{id}/confirm")
    OrderResponse confirm(@PathVariable UUID id) {
        return OrderResponse.from(orderService.confirm(id));
    }

    @PostMapping("/{id}/cancel")
    OrderResponse cancel(@PathVariable UUID id) {
        return OrderResponse.from(orderService.cancel(id));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

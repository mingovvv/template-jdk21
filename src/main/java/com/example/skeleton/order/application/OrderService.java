package com.example.skeleton.order.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.example.skeleton.order.domain.Order;
import com.example.skeleton.order.domain.OrderRepository;
import com.example.skeleton.order.domain.error.OrderNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final Clock clock;

    public OrderService(OrderRepository orderRepository, Clock clock) {
        this.orderRepository = orderRepository;
        this.clock = clock;
    }

    public Order create(String productName, int quantity) {
        Order order = Order.create(productName, quantity, Instant.now(clock));
        orderRepository.save(order);
        log.info("order.created id={} product={} quantity={}", order.id(), productName, quantity);
        return order;
    }

    public Order get(UUID id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    public List<Order> list() {
        return orderRepository.findAll();
    }

    public Order confirm(UUID id) {
        Order confirmed = orderRepository.save(get(id).confirm());
        log.info("order.confirmed id={}", id);
        return confirmed;
    }

    public Order cancel(UUID id) {
        Order cancelled = orderRepository.save(get(id).cancel());
        log.info("order.cancelled id={}", id);
        return cancelled;
    }

    public void delete(UUID id) {
        if (!orderRepository.deleteById(id)) {
            throw new OrderNotFoundException(id);
        }
        log.info("order.deleted id={}", id);
    }
}

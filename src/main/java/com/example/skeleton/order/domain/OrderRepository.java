package com.example.skeleton.order.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 주문 영속성 포트.
 * 포트는 도메인이 소유하고 구현은 {@code storage} 패키지가 담당함.
 * 그래서 서비스 레이어를 건드리지 않고 구현을 교체할 수 있음.
 */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    List<Order> findAll();

    boolean deleteById(UUID id);
}

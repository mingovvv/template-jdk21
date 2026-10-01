package com.example.skeleton.order.storage;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.example.skeleton.order.domain.Order;
import com.example.skeleton.order.domain.OrderRepository;

/**
 * 테스트 픽스처. {@link OrderRepository} 포트의 인메모리 구현임.
 * 서비스/슬라이스 테스트가 mock 없이 실제 리포지토리 동작으로 돌게 해줌.
 * 이 픽스처가 포트 계약을 지키는지는 {@code InMemoryOrderRepositoryContractTest}가 검증함.
 * 계약을 어기는 픽스처는 테스트를 거짓말로 만들기 때문임.
 * 프로덕션 구현은 {@link JpaOrderRepositoryAdapter} 하나임(ADR 0009).
 */
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<UUID, Order> orders = new ConcurrentHashMap<>();

    @Override
    public Order save(Order order) {
        orders.put(order.id(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return Optional.ofNullable(orders.get(id));
    }

    @Override
    public List<Order> findAll() {
        return orders.values().stream()
                .sorted(Comparator.comparing(Order::createdAt)
                        .thenComparing(order -> order.id().toString()))
                .toList();
    }

    @Override
    public boolean deleteById(UUID id) {
        return orders.remove(id) != null;
    }
}

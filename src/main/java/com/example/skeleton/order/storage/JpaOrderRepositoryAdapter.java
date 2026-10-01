package com.example.skeleton.order.storage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.example.skeleton.order.domain.Order;
import com.example.skeleton.order.domain.OrderRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link OrderRepository} 포트의 JPA 구현.
 * 도메인과 엔티티 사이의 변환을 여기서 하므로 application/domain은 JPA의 존재를 모름.
 */
@Repository
class JpaOrderRepositoryAdapter implements OrderRepository {

    private static final Sort CREATED_ORDER = Sort.by("createdAt", "id");

    private final OrderJpaRepository jpaRepository;

    JpaOrderRepositoryAdapter(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Order save(Order order) {
        return jpaRepository.save(OrderEntity.from(order)).toDomain();
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jpaRepository.findById(id).map(OrderEntity::toDomain);
    }

    @Override
    public List<Order> findAll() {
        return jpaRepository.findAll(CREATED_ORDER).stream()
                .map(OrderEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public boolean deleteById(UUID id) {
        if (!jpaRepository.existsById(id)) {
            return false;
        }
        jpaRepository.deleteById(id);
        return true;
    }
}

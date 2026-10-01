package com.example.skeleton.order.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import com.example.skeleton.order.domain.Order;
import com.example.skeleton.order.domain.OrderStatus;

/**
 * JPA 전용 영속성 모델.
 * 기본 생성자·가변 필드 등 JPA의 요구사항을 도메인 대신 떠맡음.
 * 도메인과의 변환은 {@link #from}/{@link #toDomain}이 담당함.
 * 도메인 모델({@code Order} record)에는 애노테이션을 붙이지 않음.
 */
@Entity
@Table(name = "orders")
class OrderEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderEntity() {
        // JPA 스펙이 요구하는 기본 생성자.
    }

    static OrderEntity from(Order order) {
        OrderEntity entity = new OrderEntity();
        entity.id = order.id();
        entity.productName = order.productName();
        entity.quantity = order.quantity();
        entity.status = order.status();
        entity.createdAt = order.createdAt();
        return entity;
    }

    Order toDomain() {
        return new Order(id, productName, quantity, status, createdAt);
    }
}

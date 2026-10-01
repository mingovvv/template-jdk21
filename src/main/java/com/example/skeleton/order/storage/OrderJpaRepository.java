package com.example.skeleton.order.storage;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 어댑터 내부에서만 쓰는 Spring Data 인터페이스.
 * 도메인 포트({@code domain.OrderRepository})와는 별개임.
 * JpaRepository를 상속해 인프라에 컴파일 의존하므로 storage 밖에서 참조하지 않음.
 */
public interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {
}

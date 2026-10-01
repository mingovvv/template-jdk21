package com.example.skeleton.order.storage;

import com.example.skeleton.order.domain.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 프로덕션 JPA 어댑터가 포트 계약을 지키는지 실제 컨텍스트(H2)로 검증함. */
@SpringBootTest
class JpaOrderRepositoryContractTest extends OrderRepositoryContractTest {

    @Autowired
    private OrderRepository orderRepository;

    @Override
    OrderRepository repository() {
        return orderRepository;
    }
}

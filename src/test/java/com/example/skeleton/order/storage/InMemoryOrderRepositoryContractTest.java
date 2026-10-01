package com.example.skeleton.order.storage;

import com.example.skeleton.order.domain.OrderRepository;

/**
 * 테스트 픽스처(인메모리)가 포트 계약을 지키는지 검증함.
 * 픽스처가 계약을 어기면 그 위에서 도는 서비스/슬라이스 테스트 전체가 거짓말이 되므로, 픽스처도 프로덕션 구현과 같은 계약 게이트를 통과해야 함.
 */
class InMemoryOrderRepositoryContractTest extends OrderRepositoryContractTest {

    private final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();

    @Override
    OrderRepository repository() {
        return orderRepository;
    }
}

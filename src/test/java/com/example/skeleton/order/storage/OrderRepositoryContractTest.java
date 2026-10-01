package com.example.skeleton.order.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.example.skeleton.order.domain.Order;
import com.example.skeleton.order.domain.OrderRepository;
import com.example.skeleton.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

/**
 * 포트 계약 테스트. {@code OrderRepository}의 모든 구현(프로덕션 JPA 어댑터, 테스트 픽스처 인메모리)은 이 계약을 지켜야 함.
 * 구현별 서브클래스가 {@link #repository()}만 제공하면 동일 검증을 상속받음(ADR 0009).
 * 새 어댑터(예: 실제 DB + Testcontainers) 추가 시에도 서브클래스 하나면 됨.
 */
abstract class OrderRepositoryContractTest {

    abstract OrderRepository repository();

    private static Order newOrder(String productName, Instant createdAt) {
        return Order.builder()
                .id(UUID.randomUUID())
                .productName(productName)
                .quantity(1)
                .status(OrderStatus.PENDING)
                .createdAt(createdAt)
                .build();
    }

    @Test
    void saveAndFindRoundtripPreservesEveryField() {
        Order order = newOrder("keyboard", Instant.parse("2026-01-01T00:00:00Z"));

        repository().save(order);

        assertThat(repository().findById(order.id())).contains(order);
    }

    @Test
    void saveIsAnUpsert() {
        Order order = newOrder("keyboard", Instant.parse("2026-01-01T00:00:00Z"));
        repository().save(order);

        repository().save(order.confirm());

        assertThat(repository().findById(order.id()).orElseThrow().status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void findUnknownReturnsEmpty() {
        assertThat(repository().findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findAllOrdersByCreationTime() {
        Order older = newOrder("older", Instant.parse("2026-01-01T00:00:00Z"));
        Order newer = newOrder("newer", Instant.parse("2026-06-01T00:00:00Z"));
        repository().save(newer);
        repository().save(older);

        List<Order> all = repository().findAll();

        assertThat(all.indexOf(older)).isNotNegative().isLessThan(all.indexOf(newer));
    }

    @Test
    void deleteReportsWhetherSomethingWasRemoved() {
        Order order = newOrder("keyboard", Instant.parse("2026-01-01T00:00:00Z"));
        repository().save(order);

        assertThat(repository().deleteById(order.id())).isTrue();
        assertThat(repository().deleteById(order.id())).isFalse();
        assertThat(repository().findById(order.id())).isEmpty();
    }
}

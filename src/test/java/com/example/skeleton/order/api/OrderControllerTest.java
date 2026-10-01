package com.example.skeleton.order.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import com.example.skeleton.common.config.SecurityConfig;
import com.example.skeleton.order.application.OrderService;
import com.example.skeleton.order.domain.Order;
import com.example.skeleton.order.storage.InMemoryOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 웹 슬라이스 테스트. 실제 보안 설정과, 인메모리 리포지토리 위에 얹은 실제 서비스 배선으로 검증함.
 */
@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, OrderControllerTest.TestBeans.class})
class OrderControllerTest {

    @TestConfiguration
    static class TestBeans {

        @Bean
        OrderService orderService() {
            return new OrderService(
                    new InMemoryOrderRepository(), Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderService orderService;

    @Test
    void requestWithoutTokenIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void listIsWrappedInPageResponse() throws Exception {
        orderService.create("monitor", 1);

        mockMvc.perform(get("/api/orders").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void createReturns201WithLocationHeader() throws Exception {
        mockMvc.perform(post("/api/orders")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productName\":\"keyboard\",\"quantity\":2}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.productName").value("keyboard"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void invalidBodyReturns400ProblemDetailWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/orders")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productName\":\"\",\"quantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void unknownOrderReturns404ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/orders/{id}", UUID.randomUUID()).with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void illegalStatusTransitionReturns409() throws Exception {
        Order order = orderService.create("keyboard", 1);
        orderService.confirm(order.id());

        mockMvc.perform(post("/api/orders/{id}/confirm", order.id()).with(jwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATUS"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void invalidUuidInPathReturns400() throws Exception {
        mockMvc.perform(get("/api/orders/{id}", "not-a-uuid").with(jwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void unsupportedHttpMethodReturns405() throws Exception {
        mockMvc.perform(patch("/api/orders").with(jwt()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unknownEndpointReturns404() throws Exception {
        mockMvc.perform(get("/api/unknown").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENDPOINT_NOT_FOUND"));
    }
}

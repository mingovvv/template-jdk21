package com.example.skeleton;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 실제 애플리케이션 컨텍스트를 통과하는 엔드투엔드 흐름.
 * 진짜 HS256 토큰으로 인증하므로 JWT 디코더 배선까지 mock 없이 검증됨.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class OrderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${skeleton.security.jwt.secret}")
    private String jwtSecret;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void openApiDocsArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void fullOrderLifecycle() throws Exception {
        String token = bearerToken("integration-test-user");

        String body = mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productName\":\"monitor\",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(body, "$.id");

        mockMvc.perform(post("/api/orders/{id}/confirm", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/orders/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(delete("/api/orders/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/orders/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void garbageTokenIsRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/orders").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pingIsPublicAndEchoesTheRequestId() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("pong"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void errorDetailFollowsTheAcceptLanguageHeader() throws Exception {
        String token = bearerToken("i18n-user");
        String missingOrderId = "3f2a0000-0000-0000-0000-000000000000";

        mockMvc.perform(get("/api/orders/{id}", missingOrderId)
                .header("Authorization", "Bearer " + token)
                .header("Accept-Language", "ko"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("주문을 찾을 수 없습니다: " + missingOrderId));

        mockMvc.perform(get("/api/orders/{id}", missingOrderId)
                .header("Authorization", "Bearer " + token)
                .header("Accept-Language", "en"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Order not found: " + missingOrderId));
    }

    @Test
    void everyResponseCarriesARequestId() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void logsFlowInRequestServiceResponseOrder(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + bearerToken("log-order-user"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productName\":\"logged\",\"quantity\":1}"))
                .andExpect(status().isCreated());

        String logs = output.getOut();
        int requestLog = logs.indexOf("req uri=/api/orders method=POST");
        int serviceLog = logs.indexOf("order.created id=");
        int responseLog = logs.indexOf("res uri=/api/orders method=POST status=201");
        assertThat(requestLog).isNotNegative();
        assertThat(serviceLog).isGreaterThan(requestLog);
        assertThat(responseLog).isGreaterThan(serviceLog);
    }

    private String bearerToken(String subject) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(jwtSecret.getBytes()));
        return jwt.serialize();
    }
}

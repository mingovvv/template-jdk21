package com.example.skeleton.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.nio.charset.StandardCharsets;

import com.example.skeleton.common.filter.RequestLoggingFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final Logger filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);

    @AfterEach
    void resetLogLevel() {
        filterLogger.setLevel(null);
    }

    @Test
    void infoModeLogsRequestLineBeforeResponseLine(CapturedOutput output) throws Exception {
        // 같은 JVM의 Spring 테스트가 local 프로파일로 레벨을 바꿔놨을 수 있으므로 명시적으로 고정함.
        filterLogger.setLevel(Level.INFO);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setQueryString("dryRun=true");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(
                request, response, (req, res) -> ((jakarta.servlet.http.HttpServletResponse) res).setStatus(201));

        String logs = output.getOut();
        int requestLog = logs.indexOf("req uri=/api/orders?dryRun=true method=POST ip=127.0.0.1");
        int responseLog = logs.indexOf("res uri=/api/orders?dryRun=true method=POST status=201 durationMs=");
        assertThat(requestLog).isNotNegative();
        assertThat(responseLog).isGreaterThan(requestLog);
    }

    @Test
    void debugModeLogsBodiesAndPreservesResponseBody(CapturedOutput output) throws Exception {
        filterLogger.setLevel(Level.DEBUG);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType("application/json");
        request.setContent("{\"productName\":\"keyboard\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            byte[] consumed = req.getInputStream().readAllBytes();
            assertThat(consumed)
                    .as("downstream must still be able to read the body")
                    .isEqualTo("{\"productName\":\"keyboard\"}".getBytes(StandardCharsets.UTF_8));
            res.setContentType("application/json");
            res.getWriter().write("{\"id\":\"abc\"}");
        });

        assertThat(output.getOut())
                .as("body is logged verbatim — no escaping, human readability first")
                .contains("body={\"productName\":\"keyboard\"}")
                .contains("body={\"id\":\"abc\"}");
        assertThat(response.getContentAsString())
                .as("copyBodyToResponse must restore the client-visible body")
                .isEqualTo("{\"id\":\"abc\"}");
    }

    @Test
    void debugModeMarksNonTextBodiesAndLogsLongOnesInFull(CapturedOutput output) throws Exception {
        filterLogger.setLevel(Level.DEBUG);
        MockHttpServletRequest binary = new MockHttpServletRequest("POST", "/api/orders");
        binary.setContentType("application/octet-stream");
        binary.setContent(new byte[]{1, 2, 3});
        filter.doFilter(binary, new MockHttpServletResponse(), (req, res) -> {
        });
        assertThat(output.getOut()).contains("body=(non-text)");

        MockHttpServletRequest longBody = new MockHttpServletRequest("POST", "/api/orders");
        longBody.setContentType("application/json");
        longBody.setContent(("{\"v\":\"" + "a".repeat(2000) + "\"}").getBytes(StandardCharsets.UTF_8));
        filter.doFilter(longBody, new MockHttpServletResponse(), (req, res) -> {
        });
        assertThat(output.getOut()).contains("a".repeat(2000) + "\"}").doesNotContain("(truncated)");
    }

    @Test
    void infrastructurePathsAreExcluded() {
        MockHttpServletRequest actuator = new MockHttpServletRequest("GET", "/actuator/health");
        actuator.setRequestURI("/actuator/health");
        MockHttpServletRequest docs = new MockHttpServletRequest("GET", "/v3/api-docs");
        docs.setRequestURI("/v3/api-docs");
        MockHttpServletRequest api = new MockHttpServletRequest("GET", "/api/orders");
        api.setRequestURI("/api/orders");

        assertThat(filter.shouldNotFilter(actuator)).isTrue();
        assertThat(filter.shouldNotFilter(docs)).isTrue();
        assertThat(filter.shouldNotFilter(api)).isFalse();
    }
}

package com.example.skeleton.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.skeleton.common.filter.CachedBodyRequest;
import jakarta.servlet.ServletInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CachedBodyRequestWrapperTest {

    private CachedBodyRequest wrap(String body) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return new CachedBodyRequest(request);
    }

    @Test
    void bodyCanBeReadRepeatedly() throws Exception {
        CachedBodyRequest request = wrap("hello");

        assertThat(request.getInputStream().readAllBytes()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        assertThat(request.getInputStream().readAllBytes())
                .as("a second consumer gets a fresh stream")
                .isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        assertThat(request.getReader().readLine()).isEqualTo("hello");
    }

    @Test
    void streamReportsReadinessAndCompletion() throws Exception {
        CachedBodyRequest request = wrap("ab");
        ServletInputStream stream = request.getInputStream();

        assertThat(stream.isReady()).isTrue();
        assertThat(stream.isFinished()).isFalse();
        assertThat(stream.read()).isEqualTo('a');
        assertThat(stream.read()).isEqualTo('b');
        assertThat(stream.isFinished()).isTrue();
    }

    @Test
    void asyncReadsAreRejected() throws Exception {
        ServletInputStream stream = wrap("x").getInputStream();

        assertThatThrownBy(() -> stream.setReadListener(null)).isInstanceOf(UnsupportedOperationException.class);
    }
}

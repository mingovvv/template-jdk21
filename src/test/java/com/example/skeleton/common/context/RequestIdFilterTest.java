package com.example.skeleton.common.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter trustOff = new RequestIdFilter(new RequestIdProperties(false));
    private final RequestIdFilter trustOn = new RequestIdFilter(new RequestIdProperties(true));

    @Test
    void generatesIdAndExposesItToMdcAndResponseHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringRequest = new AtomicReference<>();

        trustOff.doFilter(request, response, (req, res) -> mdcDuringRequest.set(MdcKey.REQUEST_ID.get()));

        assertThat(mdcDuringRequest.get())
                .as("random 64 bits, base36, fixed width")
                .matches("[0-9a-z]{13}");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(mdcDuringRequest.get());
        assertThat(MdcKey.REQUEST_ID.get())
                .as("MDC must be cleared after the request")
                .isNull();
    }

    @Test
    void ignoresClientSuppliedIdByDefault() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader(RequestIdFilter.HEADER, "upstream-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        trustOff.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getHeader(RequestIdFilter.HEADER))
                .as("trust-incoming=false: client ids are never propagated")
                .isNotEqualTo("upstream-id-123")
                .matches("[0-9a-z]{13}");
    }

    @Test
    void propagatesValidIncomingIdWhenTrustIsEnabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader(RequestIdFilter.HEADER, "upstream-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        trustOn.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("upstream-id-123");
    }

    @Test
    void regeneratesMalformedIncomingIdEvenWhenTrustIsEnabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader(RequestIdFilter.HEADER, "evil\nvalue with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        trustOn.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getHeader(RequestIdFilter.HEADER))
                .as("log-injection guard applies even to trusted upstreams")
                .matches("[0-9a-z]{13}");
    }

    @Test
    void eachRequestGetsADistinctId() throws Exception {
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse second = new MockHttpServletResponse();

        trustOff.doFilter(new MockHttpServletRequest("GET", "/a"), first, (req, res) -> {
        });
        trustOff.doFilter(new MockHttpServletRequest("GET", "/b"), second, (req, res) -> {
        });

        assertThat(first.getHeader(RequestIdFilter.HEADER)).isNotEqualTo(second.getHeader(RequestIdFilter.HEADER));
    }
}

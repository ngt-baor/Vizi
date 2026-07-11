package com.example.vizi.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTests {

    private final RateLimitFilter filter = new RateLimitFilter(
            Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void loginRouteIsRateLimitedPerClientIp() throws Exception {
        assertAllowed("POST", "/api/auth/login", "198.51.100.10", 10);
        assertBlocked("POST", "/api/auth/login", "198.51.100.10");
        assertAllowed("POST", "/api/auth/login", "198.51.100.11", 1);
    }

    @Test
    void registerAndAiRewriteRoutesAreRateLimited() throws Exception {
        assertAllowed("POST", "/api/auth/register", "198.51.100.20", 20);
        assertBlocked("POST", "/api/auth/register", "198.51.100.20");

        assertAllowed("POST", "/api/ai/text/rewrite", "198.51.100.30", 30);
        assertBlocked("POST", "/api/ai/text/rewrite", "198.51.100.30");
    }

    @Test
    void uploadRouteIsRateLimited() throws Exception {
        assertAllowed("POST", "/api/uploads/images", "198.51.100.50", 10);
        assertBlocked("POST", "/api/uploads/images", "198.51.100.50");
    }

    @Test
    void unlistedRoutesPassThrough() throws Exception {
        assertAllowed("GET", "/api/health", "198.51.100.40", 40);
    }

    private void assertAllowed(String method, String uri, String remoteAddr, int count) throws Exception {
        for (int index = 0; index < count; index++) {
            var response = doFilter(method, uri, remoteAddr);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    private void assertBlocked(String method, String uri, String remoteAddr) throws Exception {
        var response = doFilter(method, uri, remoteAddr);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("Too many requests");
    }

    private MockHttpServletResponse doFilter(String method, String uri, String remoteAddr) throws Exception {
        var request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(remoteAddr);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
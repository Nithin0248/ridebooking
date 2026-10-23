package com.nithin.ridebooking.security;
import  jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    // the "downstream" status is what the real controller would have answered
    private MockHttpServletResponse call(RateLimitFilter filter, String method, String path,
                                         String ip, int downstreamStatus) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((HttpServletResponse) res).setStatus(downstreamStatus);
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void loginIsBlockedAfterFiveFailures() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(5, 15, 1000, 60);

        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, "POST", "/api/auth/login", "1.1.1.1", 401).getStatus()).isEqualTo(401);
        }

        MockHttpServletResponse blocked = call(filter, "POST", "/api/auth/login", "1.1.1.1", 401);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
        assertThat(blocked.getContentAsString()).contains("Too many failed login attempts");

        // even the correct password is refused while locked
        assertThat(call(filter, "POST", "/api/auth/login", "1.1.1.1", 200).getStatus()).isEqualTo(429);
    }

    @Test
    void successfulLoginResetsTheFailureCount() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(5, 15, 1000, 60);

        for (int i = 0; i < 4; i++) call(filter, "POST", "/api/auth/login", "1.1.1.1", 401);
        call(filter, "POST", "/api/auth/login", "1.1.1.1", 200);
        for (int i = 0; i < 4; i++) {
            assertThat(call(filter, "POST", "/api/auth/login", "1.1.1.1", 401).getStatus()).isEqualTo(401);
        }
    }

    @Test
    void differentAddressesAreCountedSeparately() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, 15, 1000, 60);

        call(filter, "POST", "/api/auth/login", "1.1.1.1", 401);
        call(filter, "POST", "/api/auth/login", "1.1.1.1", 401);

        assertThat(call(filter, "POST", "/api/auth/login", "1.1.1.1", 401).getStatus()).isEqualTo(429);
        assertThat(call(filter, "POST", "/api/auth/login", "2.2.2.2", 401).getStatus()).isEqualTo(401);
    }

    @Test
    void otherEndpointsRejectingARequestDoNotCountAsLoginFailures() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(5, 15, 1000, 60);

        for (int i = 0; i < 10; i++) call(filter, "GET", "/api/rides/history", "1.1.1.1", 401);

        assertThat(call(filter, "POST", "/api/auth/login", "1.1.1.1", 200).getStatus()).isEqualTo(200);
    }

    @Test
    void apiIsBlockedAfterTooManyRequests() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(5, 15, 3, 60);

        for (int i = 0; i < 3; i++) {
            assertThat(call(filter, "GET", "/api/rides/history", "1.1.1.1", 200).getStatus()).isEqualTo(200);
        }

        assertThat(call(filter, "GET", "/api/rides/history", "1.1.1.1", 200).getStatus()).isEqualTo(429);
    }

    @Test
    void stripeWebhookAndNonApiPathsAreNeverLimited() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(5, 15, 1, 60);

        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, "POST", "/api/payments/webhook", "1.1.1.1", 200).getStatus()).isEqualTo(200);
            assertThat(call(filter, "GET", "/health", "1.1.1.1", 200).getStatus()).isEqualTo(200);
        }
    }
}
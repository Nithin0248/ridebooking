package com.nithin.ridebooking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)   // runs before Spring Security, so floods are stopped early
public class RateLimitFilter extends OncePerRequestFilter {

    private final AttemptTracker loginFailures;
    private final AttemptTracker apiRequests;

    public RateLimitFilter(
            @Value("${rate-limit.login-max-failures:5}") int loginMaxFailures,
            @Value("${rate-limit.login-window-minutes:15}") long loginWindowMinutes,
            @Value("${rate-limit.api-max-requests:120}") int apiMaxRequests,
            @Value("${rate-limit.api-window-seconds:60}") long apiWindowSeconds) {
        Clock clock = Clock.systemUTC();
        this.loginFailures = new AttemptTracker(loginMaxFailures, Duration.ofMinutes(loginWindowMinutes), clock);
        this.apiRequests = new AttemptTracker(apiMaxRequests, Duration.ofSeconds(apiWindowSeconds), clock);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip = request.getRemoteAddr();

        // 1. general limit on the API (Stripe's webhook comes from Stripe's servers, never limit it)
        if (path.startsWith("/api/") && !path.equals("/api/payments/webhook")) {
            if (apiRequests.isBlocked(ip)) {
                reject(response, apiRequests.retryAfterSeconds(ip), "Too many requests. Please slow down.");
                return;
            }
            apiRequests.record(ip);
        }

        // 2. stricter limit on failed logins
        boolean isLogin = "POST".equalsIgnoreCase(request.getMethod()) && path.equals("/api/auth/login");
        if (isLogin) {
            if (loginFailures.isBlocked(ip)) {
                reject(response, loginFailures.retryAfterSeconds(ip),
                        "Too many failed login attempts. Try again later.");
                return;
            }
            chain.doFilter(request, response);

            int status = response.getStatus();
            if (status == 401 || status == 403) {
                loginFailures.record(ip);
            } else if (status == 200) {
                loginFailures.reset(ip);
            }
            return;
        }

        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, long retryAfterSeconds, String message) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\""
                + message + "\"}");
    }
}
package com.example.vizi.auth;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class RateLimitFilter extends OncePerRequestFilter {

    private static final Map<String, Limit> LIMITS = Map.of(
            "POST /api/auth/login", new Limit(10, Duration.ofMinutes(1)),
            "POST /api/auth/register", new Limit(20, Duration.ofMinutes(1)),
            "POST /api/ai/text/rewrite", new Limit(30, Duration.ofMinutes(1)),
            "POST /api/uploads/images", new Limit(10, Duration.ofMinutes(1))
    );

    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    RateLimitFilter() {
        this(Clock.systemUTC());
    }

    RateLimitFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var route = request.getMethod() + " " + request.getRequestURI();
        var limit = LIMITS.get(route);
        if (limit == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!allow(clientKey(request, route), limit)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Too many requests\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean allow(String key, Limit limit) {
        var now = Instant.now(clock);
        var window = windows.compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.resetAt)) {
                return new Window(1, now.plus(limit.window));
            }
            return new Window(current.count + 1, current.resetAt);
        });
        return window.count <= limit.requests;
    }

    private static String clientKey(HttpServletRequest request, String route) {
        var forwardedFor = request.getHeader("X-Forwarded-For");
        var clientIp = forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",", 2)[0].trim();
        return route + ":" + clientIp;
    }

    private record Limit(int requests, Duration window) {
    }

    private record Window(int count, Instant resetAt) {
    }
}
package com.sketchtrench.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Fixed-window rate limiter backed by Redis (INCR + EXPIRE). Protects credential-stuffing
 * on auth endpoints and spam on public room creation.
 *
 * <p>Deliberately fail-open: if Redis is unreachable the request proceeds. Rate limiting
 * is an availability nicety; taking the whole API down because the limiter itself broke
 * would be worse than the abuse it prevents.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int LIMIT = 30;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only rate-limit the expensive-to-brute-force and cheap-to-spam endpoints.
        return !(path.startsWith("/api/auth/") || path.equals("/api/rooms"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String key = "rate:" + clientIp(request) + ":" + request.getRequestURI();
            Long count = redis.opsForValue().increment(key);
            if (count != null && count.equals(1L)) {
                redis.expire(key, WINDOW);
            }
            if (count != null && count > LIMIT) {
                response.setStatus(429);
                response.setContentType("application/json");
                objectMapper.writeValue(response.getOutputStream(),
                        Map.of("status", 429, "error", "RATE_LIMITED",
                                "message", "Too many requests, slow down"));
                return;
            }
        } catch (Exception e) {
            // fail-open: no Redis, no problem (see class javadoc)
        }
        chain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

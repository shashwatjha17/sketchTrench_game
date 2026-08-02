package com.sketchtrench.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Runs before every request (registered before the username/password filter in
 * {@code SecurityConfig}). Reads the {@code Authorization: Bearer <jwt>} header, verifies
 * the token, and — if valid — installs a {@link UserPrincipal} into the SecurityContext.
 *
 * <p>Failure is silent (no exception): the request simply proceeds unauthenticated and
 * the configured entry point returns 401 for protected paths. Security decisions are
 * always made in {@code authorizeHttpRequests}, never inside filters.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));

                @SuppressWarnings("unchecked")
                List<String> roles = claims.get("roles", List.class);

                UserPrincipal principal = new UserPrincipal(
                        Long.valueOf(claims.getSubject()),
                        claims.get("username", String.class));

                var authorities = roles.stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                        .toList();

                var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}

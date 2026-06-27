package com.leydata.backend.security;

import com.leydata.backend.userstatus.infrastructure.persistence.UserStatusRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserStatusFilter extends OncePerRequestFilter {

    private static final String CACHE_KEY_PREFIX = "user:";
    private static final String CACHE_KEY_SUFFIX = ":blocked";
    // TTL para entradas "no bloqueado" — evita que usuarios activos golpeen Postgres en cada request
    private static final Duration UNBLOCKED_TTL = Duration.ofSeconds(30);

    private final UserStatusRepository userStatusRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String keycloakId = jwtAuth.getToken().getSubject();

            if (keycloakId != null && !keycloakId.isBlank() && isBlocked(keycloakId)) {
                log.warn("Acceso bloqueado para keycloak_id={}", keycloakId);
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Tu cuenta ha sido bloqueada. Contacta al administrador.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isBlocked(String keycloakId) {
        String cacheKey = CACHE_KEY_PREFIX + keycloakId + CACHE_KEY_SUFFIX;

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return Boolean.parseBoolean(cached);
        }

        // Cache miss: consulta Postgres y actualiza Redis
        boolean blocked = userStatusRepository.findById(keycloakId)
                .map(status -> Boolean.TRUE.equals(status.getBlocked()))
                .orElse(false);

        if (blocked) {
            // Sin TTL: el bloqueo es permanente hasta que se desbloquee explícitamente
            redisTemplate.opsForValue().set(cacheKey, "true");
        } else {
            // TTL corto: renueva desde Postgres periódicamente para usuarios activos
            redisTemplate.opsForValue().set(cacheKey, "false", UNBLOCKED_TTL);
        }

        return blocked;
    }
}

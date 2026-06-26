package com.leydata.backend.security;

import com.leydata.backend.userstatus.infrastructure.persistence.UserStatusRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserStatusFilter extends OncePerRequestFilter {

    private final UserStatusRepository userStatusRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String keycloakId = jwtAuth.getToken().getSubject();

            if (keycloakId != null && !keycloakId.isBlank()) {
                userStatusRepository.findById(keycloakId).ifPresent(status -> {
                    if (Boolean.TRUE.equals(status.getBlocked())) {
                        log.warn("Acceso bloqueado para keycloak_id={}", keycloakId);
                        try {
                            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                                    "Tu cuenta ha sido bloqueada. Contacta al administrador.");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });

                if (response.isCommitted()) return;
            }
        }

        filterChain.doFilter(request, response);
    }
}

package com.leydata.backend.shared;

import com.leydata.backend.entity.Users;
import com.leydata.backend.user.infrastructure.persistence.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class SecurityContextHelper {

    private final UsersRepository usersRepository;

    private static final Set<String> BUSINESS_ROLES =
            Set.of("ADMIN", "DPO", "JEFE_DOMINIO", "USER", "TITULAR");

    public Users getAuthenticatedUser() {
        return findCurrentUser();
    }

    public Users getAuthenticatedAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) {
            throw new SecurityException("Acceso denegado: se requiere rol ADMIN");
        }
        return findCurrentUser();
    }

    public Users getAuthenticatedDpo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isDpoOrAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_DPO".equals(a.getAuthority())
                        || "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isDpoOrAdmin) {
            throw new SecurityException(
                    "Acceso denegado: solo el DPO puede revisar solicitudes de consentimiento");
        }
        return findCurrentUser();
    }

    // Busca el usuario autenticado por keycloak_id (sub) — identificador estable que nunca cambia.
    // Fallback a email para usuarios migrados antes de que se guardara keycloak_id.
    private Users findCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String keycloakId = jwtAuth.getToken().getSubject();
            if (keycloakId != null && !keycloakId.isBlank()) {
                var byId = usersRepository.findByKeycloakId(keycloakId);
                if (byId.isPresent()) return byId.get();
            }
            // Fallback: email (para cuentas que aún no tienen keycloak_id guardado)
            String email = jwtAuth.getToken().getClaimAsString("email");
            if (email != null && !email.isBlank()) {
                return usersRepository.findByEmail(email)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Usuario autenticado no encontrado en el sistema local"));
            }
        }
        return usersRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Usuario autenticado no encontrado en el sistema local"));
    }

    public String getActorRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .filter(BUSINESS_ROLES::contains)
                .findFirst()
                .orElse("UNKNOWN");
    }
}

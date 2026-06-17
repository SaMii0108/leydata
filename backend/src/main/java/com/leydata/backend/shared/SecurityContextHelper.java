package com.leydata.backend.shared;

import com.leydata.backend.entity.Users;
import com.leydata.backend.user.infrastructure.persistence.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class SecurityContextHelper {

    private final UsersRepository usersRepository;

    private static final Set<String> BUSINESS_ROLES =
            Set.of("ADMIN", "DPO", "JEFE_DOMINIO", "USER", "TITULAR");

    public Users getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return usersRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Usuario autenticado no encontrado en el sistema"));
    }

    public Users getAuthenticatedAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) {
            throw new SecurityException("Acceso denegado: se requiere rol ADMIN");
        }
        return usersRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Usuario autenticado no encontrado en el sistema local"));
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
        return usersRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Usuario autenticado no encontrado en el sistema"));
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

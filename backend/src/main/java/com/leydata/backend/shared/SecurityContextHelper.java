package com.leydata.backend.shared;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SecurityContextHelper {

    private static final Set<String> BUSINESS_ROLES =
            Set.of("ADMIN", "DPO", "JEFE_DOMINIO", "USER", "TITULAR");

    public String getKeycloakId() {
        return getJwt().getToken().getSubject();
    }

    public String getEmail() {
        String email = getJwt().getToken().getClaimAsString("email");
        return email != null ? email : getKeycloakId();
    }

    public String getName() {
        String name = getJwt().getToken().getClaimAsString("name");
        if (name != null) return name;
        String preferred = getJwt().getToken().getClaimAsString("preferred_username");
        return preferred != null ? preferred : getEmail();
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

    public void requireAdmin() {
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin) throw new SecurityException("Acceso denegado: se requiere rol ADMIN");
    }

    public void requireDpoOrAdmin() {
        boolean ok = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> "ROLE_DPO".equals(a.getAuthority()) || "ROLE_ADMIN".equals(a.getAuthority()));
        if (!ok) throw new SecurityException("Acceso denegado: se requiere rol DPO o ADMIN");
    }

    private JwtAuthenticationToken getJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) return jwtAuth;
        throw new IllegalStateException("No hay token JWT en el contexto de seguridad");
    }
}

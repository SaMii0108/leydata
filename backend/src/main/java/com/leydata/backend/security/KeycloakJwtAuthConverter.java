package com.leydata.backend.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

//Convierte el JWT de Keycloak en un objeto de autenticación que entiende Spring Security.
//Extrae los roles del claim "realm_access.roles" y los mapea a GrantedAuthority con prefijo ROLE_.
@Component
public class KeycloakJwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    //Claim donde Keycloak almacena los roles del realm
    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        //Extraemos las autoridades (roles) del token emitido por Keycloak
        Collection<GrantedAuthority> authorities = extractRealmRoles(jwt);

        //Usamos el email como nombre principal para que auth.getName() devuelva el email
        //Esto mantiene compatibilidad con los servicios que buscan usuarios por email
        String principalName = jwt.getClaimAsString("email");
        if (principalName == null) {
            //Fallback al subject (sub = UUID de Keycloak) si el token no incluye email
            principalName = jwt.getSubject();
        }

        return new JwtAuthenticationToken(jwt, authorities, principalName);
    }

    //Extrae los roles del realm desde el claim realm_access y los convierte en GrantedAuthority
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null || !realmAccess.containsKey(ROLES_CLAIM)) {
            return Collections.emptyList();
        }

        List<String> roles = (List<String>) realmAccess.get(ROLES_CLAIM);
        if (roles == null) {
            return Collections.emptyList();
        }

        return roles.stream()
                //Spring Security requiere el prefijo ROLE_ para que hasRole() y @PreAuthorize funcionen
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }
}

package com.leydata.backend.security;

import com.leydata.backend.entity.Users;
import com.leydata.backend.user.UsersRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

//Filtro que se ejecuta después de la validación del JWT de Keycloak.
//Verifica que el usuario esté activo y no bloqueado en nuestra base de datos.
//Aunque el token de Keycloak sea válido, si el admin bloqueó al usuario en nuestro sistema,
//el acceso es denegado inmediatamente sin llegar a los controladores.
//
//ESTRATEGIA DE BÚSQUEDA (resistente a cambios de email):
//  1. Busca por keycloak_id (claim "sub") — identificador estable, nunca cambia
//  2. Si no lo encuentra por keycloak_id, intenta por email (usuarios creados antes de este cambio)
//     y en ese caso auto-guarda el keycloak_id para las siguientes consultas
@Slf4j
@Component
@RequiredArgsConstructor
public class UserStatusFilter extends OncePerRequestFilter {

    private final UsersRepository usersRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Solo verificamos usuarios que ya tienen un token JWT de Keycloak validado
        if (auth instanceof JwtAuthenticationToken jwtAuth) {

            Jwt jwt = jwtAuth.getToken();
            // El claim "sub" es el ID único del usuario en Keycloak — nunca cambia
            String keycloakId = jwt.getSubject();
            // El email puede cambiar, pero lo usamos como fallback para usuarios existentes
            String email = jwt.getClaimAsString("email");

            if (keycloakId != null && !keycloakId.isBlank()) {

                // Paso 1: buscar por keycloak_id (identificador estable)
                Optional<Users> userOpt = usersRepository.findByKeycloakId(keycloakId);

                // Paso 2: si no tiene keycloak_id guardado aún, buscar por email y actualizar
                if (userOpt.isEmpty() && email != null && !email.isBlank()) {
                    userOpt = usersRepository.findByEmail(email);
                    if (userOpt.isPresent()) {
                        // Auto-sincronización: guardamos el keycloak_id para no depender del email en
                        // el futuro
                        Users user = userOpt.get();
                        log.info("Auto-sincronizando keycloak_id para usuario: {}", email);
                        user.setKeycloakId(keycloakId);
                        usersRepository.save(user);
                    }
                }

                if (userOpt.isPresent()) {
                    Users user = userOpt.get();

                    // Bloqueado permanentemente: la cuenta fue sancionada por el administrador
                    if (Boolean.TRUE.equals(user.getBlocked())) {
                        log.warn("Acceso bloqueado (cuenta bloqueada permanentemente): {}", email);
                        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                                "Tu cuenta ha sido bloqueada permanentemente. Contacta al administrador.");
                        return;
                    }

                    // Desactivado: el administrador suspendió temporalmente el acceso
                    if (!Boolean.TRUE.equals(user.getActive())) {
                        log.warn("Acceso bloqueado (cuenta desactivada): {}", email);
                        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                                "Tu cuenta ha sido desactivada. Contacta al administrador.");
                        return;
                    }
                }
                // Si el usuario no está en nuestra BD pero tiene token válido de Keycloak,
                // dejamos pasar: los servicios manejarán el error con mensaje claro
            }
        }

        filterChain.doFilter(request, response);
    }
}

package com.leydata.backend.security.filter;

import com.leydata.backend.security.jwt.JwtService;
import com.leydata.backend.auth.CustomUserDetailsService;
import com.leydata.backend.user.UsersRepository;
import com.leydata.backend.entity.Users;
import com.leydata.backend.security.exception.UserBlockedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final UsersRepository usersRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Extraemos el encabezado 'Authorization' de la petición que viene desde
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        // Validación rápida: Si no hay encabezado o no empieza con "Bearer ",
        // significa que no trae token. Lo dejamos pasar al siguiente filtro (quizás es
        // una ruta pública como /login).
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Recortamos la palabra "Bearer " (que tiene 7 caracteres) para quedarnos
        // solo con el token puro.
        jwt = authHeader.substring(7);

        // Le pedimos a nuestro JwtService que extraiga el email (username) que viene
        // encriptado en el token.
        username = jwtService.extractUsername(jwt);

        // Si encontramos un email en el token Y el usuario aún no está autenticado
        // en este ciclo...
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            try {
                // VALIDACIÓN EN TIEMPO REAL: Consultar el estado actual del usuario en BD
                Optional<Users> userOptional = usersRepository.findByEmail(username);

                if (userOptional.isEmpty()) {
                    log.warn("Token con usuario inexistente: {}", username);
                    filterChain.doFilter(request, response);
                    return;
                }

                Users user = userOptional.get();

                // Verificar si el usuario fue bloqueado permanentemente
                if (Boolean.TRUE.equals(user.getBlocked())) {
                    log.warn("Acceso denegado: usuario bloqueado permanentemente: {}", username);
                    throw new UserBlockedException(
                            "Tu cuenta ha sido bloqueada permanentemente. Contacta al administrador.");
                }

                // Verificar si el usuario fue desactivado
                if (!user.getActive()) {
                    log.warn("Acceso denegado: usuario desactivado: {}", username);
                    throw new UserBlockedException(
                            "Tu cuenta ha sido desactivada por el administrador.");
                }

                // Buscamos al usuario en la base de datos para ver si realmente existe
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                // Validamos que el token no haya expirado y pertenezca a este usuario
                if (jwtService.isTokenValid(jwt, userDetails)) {

                    // Creamos el objeto de autenticación oficial de Spring Security
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities() // Aquí van los roles (ej: ROLE_ADMIN)
                    );

                    // Le agregamos detalles extra de la petición web (como la IP, sesión, etc.)
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // ¡Pase autorizado! Guardamos la autenticación en el "Contexto de
                    // Seguridad".
                    // A partir de esta línea, Spring sabe quién es el usuario y qué permisos tiene.
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Usuario autenticado exitosamente: {}", username);
                } else {
                    log.warn("Token inválido para usuario: {}", username);
                }
            } catch (UserBlockedException e) {
                log.warn("Usuario bloqueado/desactivado detectado en petición: {} - {}", username, e.getMessage());
                response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
                return;
            } catch (Exception e) {
                log.error("Error al validar JWT para usuario: {}", username, e);
                // Continuar sin autenticación en caso de error
            }
        }

        // Finalmente, le decimos a Spring que continúe con el flujo normal hacia el
        // Controlador.
        filterChain.doFilter(request, response);
    }
}
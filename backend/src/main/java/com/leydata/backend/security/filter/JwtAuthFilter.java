package com.leydata.backend.security.filter;

import com.leydata.backend.security.jwt.JwtService;
import com.leydata.backend.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // 1. Extraemos el encabezado 'Authorization' de la petición que viene desde
        // Frontend.
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        // 2. Validación rápida: Si no hay encabezado o no empieza con "Bearer ",
        // significa que no trae token. Lo dejamos pasar al siguiente filtro (quizás es
        // una ruta pública como /login).
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Recortamos la palabra "Bearer " (que tiene 7 caracteres) para quedarnos
        // solo con el token puro.
        jwt = authHeader.substring(7);

        // 4. Le pedimos a nuestro JwtService que extraiga el email (username) que viene
        // encriptado en el token.
        username = jwtService.extractUsername(jwt);

        // 5. Si encontramos un email en el token Y el usuario aún no está autenticado
        // en este ciclo...
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Buscamos al usuario en la base de datos para ver si realmente existe
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

            // 6. Validamos que el token no haya expirado y pertenezca a este usuario
            if (jwtService.isTokenValid(jwt, userDetails)) {

                // 7. Creamos el objeto de autenticación oficial de Spring Security
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities() // Aquí van los roles (ej: ROLE_ADMIN)
                );

                // Le agregamos detalles extra de la petición web (como la IP, sesión, etc.)
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 8. ¡Pase autorizado! Guardamos la autenticación en el "Contexto de
                // Seguridad".
                // A partir de esta línea, Spring sabe quién es el usuario y qué permisos tiene.
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // 9. Finalmente, le decimos a Spring que continúe con el flujo normal hacia el
        // Controlador.
        filterChain.doFilter(request, response);
    }
}
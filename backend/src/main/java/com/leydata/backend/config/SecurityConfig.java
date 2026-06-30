package com.leydata.backend.config;

import com.leydata.backend.security.KeycloakJwtAuthConverter;
import com.leydata.backend.security.UserStatusFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

//Configuración central de seguridad.
//Con Keycloak, el backend actúa como Resource Server: no gestiona passwords ni sesiones.
//Solo valida los tokens JWT firmados por Keycloak usando la clave pública del realm.
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final KeycloakJwtAuthConverter keycloakJwtAuthConverter;
    private final UserStatusFilter userStatusFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                //CSRF deshabilitado: usamos JWT stateless, no cookies de sesión
                .csrf(csrf -> csrf.disable())

                //CORS configurado para el frontend
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                //Stateless: cada petición debe traer su token JWT de Keycloak en Authorization: Bearer
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                //Reglas de autorización por endpoint y rol
                .authorizeHttpRequests(authorize -> authorize

                        //Swagger UI y especificación OpenAPI: acceso público (solo documentación)
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml"
                        ).permitAll()

                        //Gestión de usuarios (CRUD, bloqueo, activación): solo ADMIN
                        // Excepción: un usuario puede consultar su propio perfil (validado en @PreAuthorize)
                        .requestMatchers(HttpMethod.GET, "/api/users/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/users/**").hasRole("ADMIN")

                        //Gestión de dominios: escritura solo ADMIN, lectura DPO también
                        .requestMatchers(HttpMethod.GET, "/api/domains/**").hasAnyRole("ADMIN", "DPO")
                        .requestMatchers(HttpMethod.POST, "/api/domains/**").hasRole("ADMIN")

                        //Solicitudes de propósito: crear = JEFE_DOMINIO, revisar = DPO o ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/purpose-requests").hasRole("JEFE_DOMINIO")
                        .requestMatchers(HttpMethod.GET, "/api/purpose-requests/my").hasRole("JEFE_DOMINIO")
                        .requestMatchers(HttpMethod.GET, "/api/purpose-requests/**").hasAnyRole("DPO", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/purpose-requests/*/review").hasAnyRole("DPO", "ADMIN")

                        //Auditoría: consulta de logs de operadores, solo ADMIN
                        .requestMatchers("/api/audit/**").hasRole("ADMIN")

                        // Catálogo de categorías de datos — lectura DPO/ADMIN/JEFE, escritura DPO/ADMIN
                        .requestMatchers(HttpMethod.GET, "/api/data-categories/**").hasAnyRole("DPO", "ADMIN", "JEFE_DOMINIO")
                        .requestMatchers(HttpMethod.POST, "/api/data-categories/**").hasAnyRole("DPO", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/data-categories/**").hasAnyRole("DPO", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/data-categories/**").hasAnyRole("DPO", "ADMIN")

                        // Catálogo de bases de licitud — solo lectura para operadores
                        .requestMatchers(HttpMethod.GET, "/api/legal-basis/**").hasAnyRole("DPO", "ADMIN", "JEFE_DOMINIO")

                        // Categorías de datos por finalidad + políticas de retención (más específico → va primero)
                        .requestMatchers(HttpMethod.GET, "/api/purposes/*/data-categories/**").hasAnyRole("DPO", "ADMIN", "JEFE_DOMINIO")
                        .requestMatchers(HttpMethod.POST, "/api/purposes/*/data-categories/**").hasAnyRole("DPO", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/purposes/*/data-categories/**").hasAnyRole("DPO", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/purposes/*/data-categories/**").hasAnyRole("DPO", "ADMIN")

                        // Finalidades — lectura DPO/ADMIN/JEFE, escritura DPO/ADMIN
                        .requestMatchers(HttpMethod.GET, "/api/purposes", "/api/purposes/**").hasAnyRole("DPO", "ADMIN", "JEFE_DOMINIO")
                        .requestMatchers(HttpMethod.POST, "/api/purposes").hasAnyRole("DPO", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/purposes/**").hasAnyRole("DPO", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/purposes/**").hasAnyRole("DPO", "ADMIN")

                        // ── Documentos de Privacidad — roles gestionados por @PreAuthorize ──────
                        // Escritura (create, edit, delete, workflow) → DPO (aplicado en controller)
                        .requestMatchers(HttpMethod.POST, "/api/privacy-documents").hasRole("DPO")
                        .requestMatchers(HttpMethod.PATCH, "/api/privacy-documents/**").hasRole("DPO")
                        .requestMatchers(HttpMethod.DELETE, "/api/privacy-documents/**").hasRole("DPO")
                        .requestMatchers(HttpMethod.POST, "/api/privacy-documents/**").hasRole("DPO")
                        // Lectura pública: PDF y verificación de integridad (titulares sin cuenta)
                        .requestMatchers(HttpMethod.GET, "/api/privacy-documents/{id}/pdf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/privacy-documents/{id}/verify").permitAll()
                        // Resto de lectura → cualquier usuario autenticado
                        .requestMatchers(HttpMethod.GET, "/api/privacy-documents/**").authenticated()

                        // ── Notificaciones in-app ──────────────────────────────────────────────
                        .requestMatchers("/api/notifications/**").authenticated()

                        //Cualquier otra ruta requiere autenticación válida
                        .anyRequest().authenticated())

                //Configurar el backend como Resource Server OAuth2 con validación de JWT de Keycloak
                //Spring descarga automáticamente la clave pública desde el endpoint JWKS del realm
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthConverter)))

                //Filtro propio: verifica estado activo/bloqueado en nuestra BD después de validar el JWT
                .addFilterAfter(userStatusFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        //En desarrollo se permiten todos los orígenes; restringir en producción
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

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
                        .requestMatchers(HttpMethod.GET, "/api/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/users/**").hasRole("ADMIN")

                        //Gestión de dominios: solo ADMIN
                        .requestMatchers(HttpMethod.GET, "/api/domains/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/domains/**").hasRole("ADMIN")

                        //Solicitudes de propósito: crear = JEFE_DOMINIO, revisar = DPO o ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/purpose-requests").hasRole("JEFE_DOMINIO")
                        .requestMatchers(HttpMethod.GET, "/api/purpose-requests/my").hasRole("JEFE_DOMINIO")
                        .requestMatchers(HttpMethod.GET, "/api/purpose-requests/**").hasAnyRole("DPO", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/purpose-requests/*/review").hasAnyRole("DPO", "ADMIN")

                        //Bases legales: consulta para selección en propósitos, DPO y ADMIN
                        .requestMatchers(HttpMethod.GET, "/api/legal-basis/**").hasAnyRole("ADMIN", "DPO")

                        //Auditoría: consulta de logs de operadores, solo ADMIN
                        .requestMatchers("/api/audit/**").hasRole("ADMIN")

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
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

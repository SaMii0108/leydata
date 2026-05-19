package com.leydata.backend.config;

import com.leydata.backend.security.filter.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    /*
     * Define el filtro principal (Cadena de Seguridad) por donde pasan todas las
     * peticiones HTTP.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Desactivar CSRF: Seguro porque usamos tokens JWT (Stateless).
                .csrf(csrf -> csrf.disable())

                // 2. Configuración CORS: Permite que el Frontend se comunique sin bloqueos.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 3. Sesiones Stateless: Cada petición debe traer su propio token JWT.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 4. Reglas de Autorización de Rutas (El "Portero" de la API)
                .authorizeHttpRequests(authorize -> authorize
                        // Ruta pública: Cualquiera puede intentar iniciar sesión.
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()

                        // Rutas protegidas (Primera Capa)
                        .requestMatchers(HttpMethod.POST, "/api/users").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/users/profile").authenticated()
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/domains/**").hasRole("ADMIN")
                        // del slash final
                        .requestMatchers(HttpMethod.POST, "/api/purpose-requests").hasRole("JEFE_DOMINIO")

                        // Cualquier otra ruta requiere estar autenticado
                        .anyRequest().authenticated())

                // 5. Configurar el motor de autenticación
                .authenticationProvider(authenticationProvider())

                // 6. Añadir Filtro JWT ANTES del filtro estándar de Spring.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Proveedor de Autenticación: Conecta la base de datos con el encriptador de
     * contraseñas.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        // Usa exactamente la lógica original que funciona en tu versión
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Gestor de Autenticación: Maneja el flujo de login.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration authConfig)
            throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * Motor de encriptación de contraseñas con BCrypt.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configuración CORS (Cross-Origin Resource Sharing) para el Frontend.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Permite cualquier origen (para desarrollo). Cambiar en producción.
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
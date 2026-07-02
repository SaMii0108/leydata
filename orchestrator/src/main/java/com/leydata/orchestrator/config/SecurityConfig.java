package com.leydata.orchestrator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * JWKS del IdP externo del cliente (su CRM, ERP, etc.)
     * En dev local apunta al realm empresa-cliente de nuestro Keycloak.
     * En producción apunta al IdP real del cliente (configurable por implementación).
     */
    @Value("${leydata.external-jwks-uri}")
    private String externalJwksUri;

    /**
     * INBOUND: decoder que valida el JWT que llega desde el sistema del cliente.
     * Apunta al JWKS externo — nunca al Keycloak interno.
     */
    @Bean
    public ReactiveJwtDecoder externalJwtDecoder() {
        if (externalJwksUri == null || externalJwksUri.isBlank()) {
            throw new IllegalStateException(
                "EXTERNAL_JWKS_URI no configurado. " +
                "En dev local: asegúrate de haber creado el realm empresa-cliente en Keycloak " +
                "y que la variable esté seteada en docker-compose.override.yml");
        }
        return NimbusReactiveJwtDecoder.withJwkSetUri(externalJwksUri).build();
    }

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http,
                                                      ReactiveJwtDecoder externalJwtDecoder) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(ex -> ex
                        .pathMatchers("/actuator/health", "/actuator/prometheus", "/actuator/metrics").permitAll()
                        .pathMatchers("/consent/**").authenticated()
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().denyAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtDecoder(externalJwtDecoder))
                )
                .build();
    }
}

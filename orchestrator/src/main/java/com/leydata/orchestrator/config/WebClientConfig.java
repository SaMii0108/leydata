package com.leydata.orchestrator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${leydata.backend-url}")
    private String leydataBackendUrl;

    @Bean
    public WebClient leydataClient(ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter) {
        return WebClient.builder()
                .baseUrl(leydataBackendUrl)
                // Inyecta automáticamente el Bearer token de Keycloak (client_credentials)
                .filter(oauth2Filter)
                // Identifica al Orquestador como fuente de la petición
                .defaultHeader("X-Internal-Forwarded-By", "leydata-orchestrator")
                .build();
    }
}

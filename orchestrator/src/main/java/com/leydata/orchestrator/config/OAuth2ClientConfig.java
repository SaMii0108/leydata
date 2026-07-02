package com.leydata.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;

/**
 * OUTBOUND: gestiona el token client_credentials que el Orquestador usa
 * para llamar al backend LeyData via WebClient.
 * Opera completamente separado del flujo inbound de validación JWT.
 */
@Configuration
public class OAuth2ClientConfig {

    @Bean
    public ServerOAuth2AuthorizedClientExchangeFilterFunction leydataOAuth2Filter(
            ReactiveClientRegistrationRepository clientRegistrationRepository,
            ReactiveOAuth2AuthorizedClientService authorizedClientService) {

        var manager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientService);

        var filter = new ServerOAuth2AuthorizedClientExchangeFilterFunction(manager);
        // Usar el registration "leydata-system" definido en application.yml
        filter.setDefaultClientRegistrationId("leydata-system");
        return filter;
    }
}

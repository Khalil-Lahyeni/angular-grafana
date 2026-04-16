package actia.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.security.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        // ✅ DOIT être l'origine exacte (pas "*") pour que allowCredentials fonctionne
        corsConfig.setAllowedOrigins(List.of(frontendUrl));

        corsConfig.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));

        corsConfig.setAllowedHeaders(List.of("*"));

        // ✅ OBLIGATOIRE pour envoyer le cookie SESSION
        corsConfig.setAllowCredentials(true);

        // ✅ Expose le header Set-Cookie pour que le browser le lise
        corsConfig.setExposedHeaders(List.of("Set-Cookie"));

        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
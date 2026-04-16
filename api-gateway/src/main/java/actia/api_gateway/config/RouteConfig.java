package actia.api_gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;

@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()

                // ── Collector Service - Trains ──
                .route("Trains", route -> route
                        .path("/api/trains/**")
                        .filters(filter -> filter
                                .stripPrefix(2)
                                .tokenRelay()
                        )
                        .uri("http://localhost:8881")
                )

                // ── Grafana (SSO indépendant via Keycloak) ──
                // ⚠️ PAS de stripPrefix : Grafana est configuré avec
                //    serve_from_sub_path = true et s'attend à recevoir /grafana/*
                // ⚠️ PAS de tokenRelay : Grafana gère SA PROPRE session OAuth
                //    avec Keycloak, indépendamment de la session Gateway
                .route("Grafana", route -> route
                        .path("/grafana/**")
                        .uri("http://grafana:3000")
                )

                .build();
    }
}
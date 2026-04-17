package actia.api_gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.WebFilterExchange;

import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.*;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.ServerWebExchange;

import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.SecurityContextServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.WebSessionServerLogoutHandler;
import org.springframework.http.ResponseCookie;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${app.logout-url:http://localhost:4200}")
    private String logoutUrl;

    private final ReactiveClientRegistrationRepository clientRegistrationRepository;

    public SecurityConfig(ReactiveClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http, ReactiveClientRegistrationRepository clientRegistrationRepository) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())  // ← CookieServerCsrf...
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(
                                new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)  // ← HttpStatusServerEntryPoint
                        )
                )
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers("/login/oauth2/code/**").permitAll()
                        .pathMatchers("/oauth2/**").permitAll()
                        .pathMatchers("/api/trains/collector/ping").permitAll()
                        .pathMatchers("/logout").permitAll()
                        .anyExchange().authenticated()
                )

                .oauth2Login(oauth2 -> oauth2
                        .authenticationSuccessHandler(
                                new RedirectServerAuthenticationSuccessHandler(logoutUrl)  // ← RedirectServer...
                        )
                        .authenticationFailureHandler(
                                new RedirectServerAuthenticationFailureHandler(logoutUrl)  // ← RedirectServer...
                        )
                )
                .logout(logout -> logout
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository))
                        .logoutHandler(deleteCookiesLogoutHandler())
                        .requiresLogout(new PathPatternParserServerWebExchangeMatcher("/logout"))  // ← ServerWebExchangeMatcher
                );

        return http.build();
    }

    @Bean
    public OidcClientInitiatedServerLogoutSuccessHandler oidcLogoutSuccessHandler(  // ← Server variant
                                                                                    ReactiveClientRegistrationRepository clientRegistrationRepository
    ) {
        OidcClientInitiatedServerLogoutSuccessHandler handler =
                new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository);
        handler.setPostLogoutRedirectUri(logoutUrl);
        return handler;
    }

    @Bean
    public ServerLogoutHandler deleteCookiesLogoutHandler() {
        return new DelegatingServerLogoutHandler(// invalide la session (= invalidateHttpSession)
                new SecurityContextServerLogoutHandler(),  // clear le SecurityContext
                cookiesClearingLogoutHandler("SESSION", "grafana_session", "grafana_session_expiry"),
                new WebSessionServerLogoutHandler()
                );
    }

    private ServerLogoutHandler cookiesClearingLogoutHandler(String... cookieNames) {
        return (exchange, authentication) -> {
            ServerHttpResponse response = exchange.getExchange().getResponse();

            for (String cookieName : cookieNames) {
                ResponseCookie expiredCookie = ResponseCookie.from(cookieName, "")
                        .maxAge(Duration.ZERO)   // expire immédiatement
                        .path("/")
                        .httpOnly(false)
                        .build();
                response.addCookie(expiredCookie);
            }

            return Mono.empty();
        };
    }

}


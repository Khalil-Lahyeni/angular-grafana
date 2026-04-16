package actia.api_gateway.security;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.DelegatingServerAuthenticationEntryPoint;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.logout.DelegatingServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.WebFilter;

import reactor.core.publisher.Mono;


@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String FRONTEND_REDIRECT_ATTR = "FRONTEND_REDIRECT_URI";
    private static final String ID_TOKEN_ATTR          = "OIDC_ID_TOKEN";

    private static final String KEYCLOAK_LOGOUT_URL =
            "http://localhost:8080/realms/fleet-management/protocol/openid-connect/logout";

    private final URI frontendUrl;

    public SecurityConfig(@Value("${app.security.frontend-url:http://localhost:4200}") String frontendUrl) {
        this.frontendUrl = URI.create(frontendUrl);
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         ReactiveClientRegistrationRepository clientRegistrationRepository) {

        DelegatingServerAuthenticationEntryPoint entryPoint = new DelegatingServerAuthenticationEntryPoint(
                new DelegatingServerAuthenticationEntryPoint.DelegateEntry(
                        new PathPatternParserServerWebExchangeMatcher("/api/**"),
                        new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED))
        );
        entryPoint.setDefaultEntryPoint(
                new RedirectServerAuthenticationEntryPoint("/oauth2/authorization/keycloak"));

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health", "/actuator/info", "/error", "/favicon.ico").permitAll()
                        .pathMatchers("/oauth2/**", "/login/**", "/api/public/**").permitAll()
                        .pathMatchers("/logout").permitAll()
                        // ✅ Grafana a son propre flow OAuth avec Keycloak → le Gateway
                        //    doit laisser passer SANS intercepter
                        .pathMatchers("/grafana/**").permitAll()
                        .anyExchange().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                .oauth2Login(oauth2 -> oauth2.authenticationSuccessHandler(frontendRedirectSuccessHandler()))
                .logout(logout -> logout
                        .requiresLogout(new PathPatternParserServerWebExchangeMatcher("/logout", HttpMethod.POST))
                        .logoutHandler(new DelegatingServerLogoutHandler(sessionAndCookieLogoutHandler()))
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository))
                )
                .build();
    }

    /**
     * Intercepte GET /login?error (généré par Spring Security quand le flow OAuth2
     * échoue sur un 2ème onglet alors qu'une session est déjà active sur un autre onglet).
     */
    @Bean
    public WebFilter loginErrorFilter() {
        return (exchange, chain) -> {
            String path  = exchange.getRequest().getPath().value();
            String query = exchange.getRequest().getURI().getQuery();

            boolean isLoginError = path.equals("/login")
                    && query != null && query.contains("error");

            if (!isLoginError) {
                return chain.filter(exchange);
            }

            return exchange.getSession()
                    .flatMap(session -> {
                        boolean hasSession = !session.getAttributes().isEmpty();
                        URI target = hasSession
                                ? frontendUrl
                                : URI.create("/oauth2/authorization/keycloak");

                        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                        exchange.getResponse().getHeaders().setLocation(target);
                        return exchange.getResponse().setComplete();
                    });
        };
    }

    @Bean
    public WebFilter idTokenCaptureFilter() {
        return (exchange, chain) -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof OAuth2AuthenticationToken)
                .cast(OAuth2AuthenticationToken.class)
                .filter(auth -> auth.getPrincipal() instanceof OidcUser)
                .flatMap(auth -> {
                    OidcUser oidcUser = (OidcUser) auth.getPrincipal();
                    String idToken = oidcUser.getIdToken().getTokenValue();
                    return exchange.getSession()
                            .doOnNext(session -> {
                                if (!session.getAttributes().containsKey(ID_TOKEN_ATTR)) {
                                    session.getAttributes().put(ID_TOKEN_ATTR, idToken);
                                }
                            });
                })
                .then(chain.filter(exchange));
    }

    @Bean
    public WebFilter getLogoutFilter() {
        return (exchange, chain) -> {
            boolean isGetLogout = exchange.getRequest().getMethod() == HttpMethod.GET
                    && exchange.getRequest().getPath().value().equals("/logout");

            if (!isGetLogout) {
                return chain.filter(exchange);
            }

            return exchange.getSession()
                    .flatMap(session -> {
                        String idToken = (String) session.getAttributes().get(ID_TOKEN_ATTR);
                        return session.invalidate().thenReturn(idToken != null ? idToken : "");
                    })
                    .defaultIfEmpty("")
                    .flatMap(idToken -> {
                        ResponseCookie expiredCookie = ResponseCookie.from("SESSION", "")
                                .path("/")
                                .httpOnly(true)
                                .maxAge(Duration.ZERO)
                                .build();
                        exchange.getResponse().addCookie(expiredCookie);

                        String postLogoutUri = frontendUrl + "/oauth2/authorization/keycloak";
                        StringBuilder keycloakLogout = new StringBuilder(KEYCLOAK_LOGOUT_URL)
                                .append("?post_logout_redirect_uri=")
                                .append(URLEncoder.encode(postLogoutUri, StandardCharsets.UTF_8))
                                .append("&client_id=actia-app");

                        if (StringUtils.hasText(idToken)) {
                            keycloakLogout.append("&id_token_hint=")
                                    .append(URLEncoder.encode(idToken, StandardCharsets.UTF_8));
                        }

                        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
                        exchange.getResponse().getHeaders().setLocation(URI.create(keycloakLogout.toString()));
                        return exchange.getResponse().setComplete();
                    });
        };
    }

    @Bean
    public WebFilter frontendRedirectCaptureFilter() {
        return (exchange, chain) -> {
            String path = exchange.getRequest().getPath().value();
            if (path.startsWith("/oauth2/authorization/")) {
                String requestedRedirect = exchange.getRequest().getQueryParams().getFirst("redirect_uri");
                if (StringUtils.hasText(requestedRedirect)) {
                    URI redirectCandidate = URI.create(requestedRedirect);
                    if (isTrustedFrontendRedirect(redirectCandidate)) {
                        return exchange.getSession()
                                .doOnNext(session -> session.getAttributes()
                                        .put(FRONTEND_REDIRECT_ATTR, requestedRedirect))
                                .then(chain.filter(exchange));
                    }
                }
            }
            return chain.filter(exchange);
        };
    }

    private ServerAuthenticationSuccessHandler frontendRedirectSuccessHandler() {
        return (webFilterExchange, authentication) -> webFilterExchange.getExchange()
                .getSession()
                .flatMap(session -> {
                    URI target = frontendUrl;
                    Object requestedRedirect = session.getAttribute(FRONTEND_REDIRECT_ATTR);
                    if (requestedRedirect instanceof String redirect && StringUtils.hasText(redirect)) {
                        URI redirectUri = URI.create(redirect);
                        if (isTrustedFrontendRedirect(redirectUri)) {
                            target = redirectUri;
                        }
                    }
                    session.getAttributes().remove(FRONTEND_REDIRECT_ATTR);
                    return sendRedirect(webFilterExchange, target);
                });
    }

    private ServerLogoutHandler sessionAndCookieLogoutHandler() {
        return (webFilterExchange, authentication) -> webFilterExchange.getExchange()
                .getSession()
                .flatMap(session -> session.invalidate()
                        .then(clearSessionCookie(webFilterExchange)));
    }

    private ServerLogoutSuccessHandler oidcLogoutSuccessHandler(
            ReactiveClientRegistrationRepository clientRegistrationRepository) {

        OidcClientInitiatedServerLogoutSuccessHandler handler =
                new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository);
        handler.setPostLogoutRedirectUri("{baseUrl}/oauth2/authorization/keycloak");
        return handler;
    }

    private Mono<Void> clearSessionCookie(WebFilterExchange webFilterExchange) {
        ResponseCookie cookie = ResponseCookie.from("SESSION", "")
                .path("/")
                .httpOnly(true)
                .maxAge(Duration.ZERO)
                .build();
        webFilterExchange.getExchange().getResponse().addCookie(cookie);
        return Mono.empty();
    }

    private Mono<Void> sendRedirect(WebFilterExchange webFilterExchange, URI target) {
        webFilterExchange.getExchange().getResponse().setStatusCode(HttpStatus.FOUND);
        webFilterExchange.getExchange().getResponse().getHeaders().setLocation(target);
        return webFilterExchange.getExchange().getResponse().setComplete();
    }

    private boolean isTrustedFrontendRedirect(URI candidate) {
        return candidate.getHost() != null
                && candidate.getHost().equalsIgnoreCase(frontendUrl.getHost())
                && normalizePort(candidate) == normalizePort(frontendUrl);
    }

    private int normalizePort(URI uri) {
        if (uri.getPort() != -1) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
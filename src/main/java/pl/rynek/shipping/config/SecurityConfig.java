package pl.rynek.shipping.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import pl.rynek.shipping.common.ProblemJson;

/**
 * Przesyłki mają endpointy {@code seller} i {@code user}, więc serwis sam sprawdza JWT z identity-service (klucze z JWKS),
 * także przy {@code JWT_ENFORCE=false} w gateway. Cennik, punkty odbioru, webhooki przewoźnika i strona symulatora są
 * publiczne (webhook sprawdza podpis).
 */
@Configuration
public class SecurityConfig {

    private static final RequestMatcher PUBLIC = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher("/internal/**"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/shipping/methods"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/shipping/pickup-points"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/shipping/webhooks/{carrier}"),
            PathPatternRequestMatcher.withDefaults().matcher("/api/v1/shipping/sandbox/{shipmentId}"));

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Bezstanowe API z tokenem Bearer: bez sesji i ciasteczek, więc bez CSRF.
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(PUBLIC).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(ignoreTokenOnPublic())
                        .jwt(jwt -> {
                        })
                        .authenticationEntryPoint(unauthorized()))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(unauthorized())
                        .accessDeniedHandler((request, response, ex) ->
                                ProblemJson.write(request, response, HttpStatus.FORBIDDEN, "Nie masz uprawnień do tej operacji.")))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${rynek.jwt.jwk-set-uri}") String jwkSetUri, @Value("${rynek.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    private static BearerTokenResolver ignoreTokenOnPublic() {
        DefaultBearerTokenResolver bearer = new DefaultBearerTokenResolver();
        return request -> PUBLIC.matches(request) ? null : bearer.resolve(request);
    }

    private static AuthenticationEntryPoint unauthorized() {
        return (request, response, ex) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            ProblemJson.write(request, response, HttpStatus.UNAUTHORIZED, "Zaloguj się, aby kontynuować.");
        };
    }
}

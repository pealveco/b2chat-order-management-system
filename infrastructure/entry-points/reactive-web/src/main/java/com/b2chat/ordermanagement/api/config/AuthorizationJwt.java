package com.b2chat.ordermanagement.api.config;

import com.b2chat.ordermanagement.api.error.ApiError;
import com.b2chat.ordermanagement.api.error.ApiErrorResponse;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class AuthorizationJwt implements WebFluxConfigurer {

    private final String issuerUri;
    private final String clientId;
    private final String jsonExpRoles;
    private final String secret;

    private final ObjectMapper mapper;
    private static final String ROLE = "ROLE_";
    private static final String AZP = "azp";

    public AuthorizationJwt(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
                         @Value("${spring.security.oauth2.resourceserver.jwt.client-id}") String clientId,
                         @Value("${jwt.json-exp-roles}") String jsonExpRoles,
                         @Value("${jwt.secret}") String secret,
                         ObjectMapper mapper) {
        this.issuerUri = issuerUri;
        this.clientId = clientId;
        this.jsonExpRoles = jsonExpRoles;
        this.secret = secret;
        this.mapper = mapper;
    }

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .exceptionHandling(exceptionHandling -> exceptionHandling
                    .authenticationEntryPoint((exchange, exception) -> writeSecurityError(exchange,
                            HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                            "Authorization Bearer token is required or invalid"))
                    .accessDeniedHandler((exchange, exception) -> writeSecurityError(exchange,
                            HttpStatus.FORBIDDEN, "FORBIDDEN",
                            "Authenticated user is not allowed to access this resource")))
            .authorizeExchange(authorize -> authorize
                    .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .pathMatchers(HttpMethod.GET, "/**").permitAll()
                    .pathMatchers(HttpMethod.POST, "/users").permitAll()
                    .pathMatchers(HttpMethod.POST, "/auth/token").permitAll()
                    .pathMatchers(HttpMethod.POST, "/**").authenticated()
                    .pathMatchers(HttpMethod.PUT, "/**").authenticated()
                    .pathMatchers(HttpMethod.DELETE, "/**").authenticated()
                    .anyExchange().authenticated())
            .oauth2ResourceServer(oauth2 ->
                    oauth2
                            .authenticationEntryPoint((exchange, exception) -> writeSecurityError(exchange,
                                    HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                                    "Authorization Bearer token is required or invalid"))
                            .authenticationFailureHandler((webFilterExchange, exception) -> writeSecurityError(
                                    webFilterExchange.getExchange(),
                                    HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                                    "Authorization Bearer token is required or invalid"))
                            .jwt(jwtSpec ->
                                    jwtSpec
                                    .jwtDecoder(jwtDecoder())
                                    .jwtAuthenticationConverter(grantedAuthoritiesExtractor())
                            )
            );
        return http.build();
    }

    public ReactiveJwtDecoder jwtDecoder() {
        var defaultValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        var audienceValidator = new JwtClaimValidator<String>(AZP,
                azp -> azp != null && !azp.isEmpty() && azp.equals(clientId));
        var tokenValidator = new DelegatingOAuth2TokenValidator<>(defaultValidator, audienceValidator);
        var jwtDecoder = NimbusReactiveJwtDecoder
                .withSecretKey(secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        jwtDecoder.setJwtValidator(tokenValidator);
        return jwtDecoder;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    public Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtractor() {
        var jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(jwt ->
                getRoles(jwt.getClaims(), jsonExpRoles)
                .stream()
                .map(ROLE::concat)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList()));
        return new ReactiveJwtAuthenticationConverterAdapter(jwtConverter);
    }

    private List<String> getRoles(Map<String, Object> claims, String jsonExpClaim) {
        try {
            var json = mapper.writeValueAsString(claims);
            var chunk = mapper.readTree(json).at(jsonExpClaim);
            return mapper.readerFor(new TypeReference<List<String>>() {})
                    .readValue(chunk);
        } catch (RuntimeException exception) {
            log.debug("JWT roles claim is missing or invalid");
            return List.of();
        }
    }

    private SecretKey secretKey() {
        var bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("jwt.secret must be at least 32 bytes for HS256");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    private Mono<Void> writeSecurityError(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var path = exchange.getRequest().getPath().value();
        var error = ApiError.of(code, message, status.value(), path);
        var bytes = mapper.writeValueAsBytes(new ApiErrorResponse(error));
        var buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}

package com.b2chat.ordermanagement.api.auth;

import com.b2chat.ordermanagement.model.auth.gateways.TokenIssuerPort;
import com.b2chat.ordermanagement.model.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class JwtTokenIssuerAdapter implements TokenIssuerPort {
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String clientId;
    private final Duration expiration;

    public JwtTokenIssuerAdapter(JwtEncoder jwtEncoder,
                                 @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
                                 @Value("${spring.security.oauth2.resourceserver.jwt.client-id}") String clientId,
                                 @Value("${jwt.expiration-minutes:60}") long expirationMinutes) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.clientId = clientId;
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    @Override
    public Mono<String> issue(User user) {
        return Mono.fromCallable(() -> {
            var now = Instant.now();
            var claims = JwtClaimsSet.builder()
                    .issuer(issuer)
                    .issuedAt(now)
                    .expiresAt(now.plus(expiration))
                    .subject(user.getId().toString())
                    .claim("userId", user.getId().toString())
                    .claim("email", user.getEmail().getValue())
                    .claim("azp", clientId)
                    .claim("roles", List.of("USER"))
                    .build();

            var header = JwsHeader.with(MacAlgorithm.HS256).build();
            return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        });
    }
}

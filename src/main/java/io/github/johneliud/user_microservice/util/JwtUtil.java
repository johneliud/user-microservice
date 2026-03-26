package io.github.johneliud.user_microservice.util;

import io.github.johneliud.user_microservice.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private static final String MFA_SCOPE = "mfa";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.mfa-expiration}")
    private long MFA_TOKEN_EXPIRATION;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        String role = user.getRoles().stream()
                .findFirst()
                .map(Enum::name)
                .orElse("ROLE_USER");

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String generateMfaToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("scope", MFA_SCOPE)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + MFA_TOKEN_EXPIRATION))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Claims validateMfaToken(String token) {
        Claims claims = validateToken(token);
        if (!MFA_SCOPE.equals(claims.get("scope", String.class))) {
            throw new IllegalArgumentException("Not a valid MFA token");
        }
        return claims;
    }

    public String getUserId(Claims claims) {
        return claims.getSubject();
    }

    public String getRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public long getExpiration() {
        return expiration;
    }
}
package com.loanapproval.dss.security;

import com.loanapproval.dss.auth.UserAccount;
import com.loanapproval.dss.shared.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMinutes;
    private final long refreshTokenExpirationDays;

    public JwtService(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.jwt.expiration-minutes:120}") long accessTokenExpirationMinutes,
        @Value("${app.jwt.refresh-expiration-days:30}") long refreshTokenExpirationDays
    ) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
    }

    public String generateAccessToken(UserAccount user) {
        return generateToken(user, accessTokenExpirationMinutes, ChronoUnit.MINUTES, "access");
    }

    public String generateRefreshToken(UserAccount user) {
        return generateToken(user, refreshTokenExpirationDays, ChronoUnit.DAYS, "refresh");
    }

    public AuthenticatedUser parseAccessToken(String token) {
        return parseTokenByType(token, "access");
    }

    public AuthenticatedUser parseRefreshToken(String token) {
        return parseTokenByType(token, "refresh");
    }

    private String generateToken(UserAccount user, long expirationAmount, ChronoUnit unit, String tokenType) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Math.max(expirationAmount, 1), unit);

        return Jwts.builder()
            .subject(user.email())
            .claim("uid", user.id())
            .claim("role", user.role().name())
            .claim("token_type", tokenType)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey)
            .compact();
    }

    private AuthenticatedUser parseTokenByType(String token, String expectedType) {
        Claims claims = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();

        String actualType = claims.get("token_type", String.class);
        if (!expectedType.equals(actualType)) {
            throw new JwtException("Token type mismatch");
        }

        Number userId = claims.get("uid", Number.class);
        String roleValue = claims.get("role", String.class);
        String email = claims.getSubject();

        return new AuthenticatedUser(userId.longValue(), email, Role.valueOf(roleValue));
    }
}

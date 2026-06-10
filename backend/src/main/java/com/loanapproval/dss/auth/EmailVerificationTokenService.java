package com.loanapproval.dss.auth;

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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EmailVerificationTokenService {

    private static final String TOKEN_TYPE = "email_verification";

    private final SecretKey signingKey;
    private final long expiresMinutes;

    public EmailVerificationTokenService(
        @Value("${app.jwt.secret}") String secret,
        @Value("${app.auth.email-verification.expires-minutes:30}") long expiresMinutes
    ) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expiresMinutes = expiresMinutes;
    }

    public String generateToken(Long userId, String email) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Math.max(expiresMinutes, 1), ChronoUnit.MINUTES);
        return Jwts.builder()
            .subject(email)
            .claim("token_type", TOKEN_TYPE)
            .claim("uid", userId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey)
            .compact();
    }

    public EmailVerificationTokenPayload parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            String tokenType = claims.get("token_type", String.class);
            if (!TOKEN_TYPE.equals(tokenType)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Liên kết xác minh email không hợp lệ");
            }
            Number userId = claims.get("uid", Number.class);
            String email = claims.getSubject();
            if (userId == null || email == null || email.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Liên kết xác minh email bị thiếu dữ liệu");
            }
            return new EmailVerificationTokenPayload(userId.longValue(), email);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Liên kết xác minh email đã hết hạn hoặc không hợp lệ",
                ex
            );
        }
    }

    public record EmailVerificationTokenPayload(Long userId, String email) {
    }
}

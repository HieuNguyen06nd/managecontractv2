package com.hieunguyen.ManageContract.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;
import java.util.function.Function;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtUtil {

    @Value("${jwt.secret-key}")
    private String secret;

    @Value("${jwt.expiration-time}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration-time}")
    private long refreshTokenExpiration;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ===================== TẠO TOKEN =====================

    public String generateToken(String email, List<String> roleKeys) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roleKeys); // plural
        return generateTokenWithClaims(email, claims);
    }

    public String generateTokenWithClaims(String subject, Map<String, Object> extraClaims) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(subject) // email
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(String email) {
        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ===================== TRÍCH XUẤT THÔNG TIN =====================

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Object value = extractClaim(token, claims -> claims.get("roles"));
        if (value instanceof List<?>) {
            return ((List<?>) value).stream()
                    .map(Object::toString)
                    .toList();
        }
        return Collections.emptyList();
    }


    public Long extractAccountId(String token) {
        Object value = extractClaim(token, claims -> claims.get("accountId"));
        return value instanceof Integer ? ((Integer) value).longValue() : (Long) value;
    }

    @SuppressWarnings("unchecked")
    public List<String> extractPermissions(String token) {
        Object value = extractClaim(token, claims -> claims.get("permissions"));
        if (value instanceof List<?>) {
            return ((List<?>) value).stream()
                    .map(Object::toString)
                    .toList();
        }
        return Collections.emptyList();
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // ===================== VALIDATE TOKEN =====================

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("Token không hợp lệ: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractEmail(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
}

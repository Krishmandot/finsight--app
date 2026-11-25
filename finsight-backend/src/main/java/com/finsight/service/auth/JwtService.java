package com.finsight.service.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    static final String INSECURE_DEFAULT_MARKER = "changeme";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Environment environment;

    @Value("${finsight.jwt.secret}")
    private String jwtSecret;

    @Value("${finsight.jwt.access-token-expiry-ms:900000}")
    private long accessTokenExpiryMs;

    private SecretKey signingKey;

    public JwtService(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validateSecret() {
        byte[] keyBytes = jwtSecret == null ? new byte[0] : jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "FINSIGHT_JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes long");
        }
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (production && jwtSecret.contains(INSECURE_DEFAULT_MARKER)) {
            throw new IllegalStateException("FINSIGHT_JWT_SECRET must be set to a unique secret in production");
        }
        signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UserDetails userDetails) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .claims(Map.of())
            .subject(userDetails.getUsername())
            .issuedAt(new Date(now))
            .expiration(new Date(now + accessTokenExpiryMs))
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();
    }

    /**
     * Generates a 256-bit random opaque refresh token. Only its SHA-256 digest is persisted.
     */
    public String generateRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Refresh tokens are high-entropy random values, so a fast unsalted digest is sufficient and lets the
     * token be looked up by its hash in O(1). (BCrypt is reserved for low-entropy passwords.)
     */
    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    /** Signature and expiry are verified while parsing; this additionally checks the subject. */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        Claims claims = extractAllClaims(token);
        return userDetails.getUsername().equalsIgnoreCase(claims.getSubject())
            && claims.getExpiration().after(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}

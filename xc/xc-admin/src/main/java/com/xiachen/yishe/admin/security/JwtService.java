package com.xiachen.yishe.admin.security;

import com.xiachen.yishe.domain.entity.Staff;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlSeconds;

    public JwtService(@Value("${jwt.secret:}") String secret,
                      @Value("${jwt.ttl-hours:8}") long ttlHours) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret 未配置或长度不足 32 字节，拒绝以无鉴权方式启动");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = ttlHours * 3600;
    }

    public String issue(Staff staff) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(staff.getId()))
                .claim("username", staff.getUsername())
                .claim("name", staff.getDisplayName())
                .claim("role", staff.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key);
        if (staff.getStoreId() != null) {
            builder.claim("storeId", staff.getStoreId());
        }
        return builder.compact();
    }

    public StaffPrincipal parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        Long storeId = claims.get("storeId", Number.class) == null
                ? null : claims.get("storeId", Number.class).longValue();
        return new StaffPrincipal(
                Long.valueOf(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("name", String.class),
                claims.get("role", String.class),
                storeId);
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }
}

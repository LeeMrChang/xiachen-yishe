package com.xiachen.yishe.api.security;

import com.xiachen.yishe.domain.entity.AppUser;
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
public class UserJwtService {

    private static final String SCOPE = "user";

    private final SecretKey key;
    private final long ttlSeconds;

    public UserJwtService(@Value("${jwt.secret:}") String secret,
                          @Value("${jwt.ttl-days:7}") long ttlDays) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret 未配置或长度不足 32 字节，拒绝以无鉴权方式启动");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = ttlDays * 24 * 3600;
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("scope", SCOPE)
                .claim("appid", user.getAppid())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    public Long parseUserId(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        if (!SCOPE.equals(claims.get("scope", String.class))) {
            throw new IllegalArgumentException("not a user token");
        }
        return Long.valueOf(claims.getSubject());
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }
}

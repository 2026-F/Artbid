package com.artbid.member.auth;

import com.artbid.member.domain.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final long validityMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.validity-ms:3600000}") long validityMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityMs = validityMs;
    }

    // role까지 같이 서명해서 넣어둠 — 나중에 MSA로 쪼개져도 다른 서비스가
    // 매번 회원 DB를 조회하지 않고 토큰만 보고 권한 판단할 수 있게 하기 위함.
    public String generateToken(Long memberId, MemberRole role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMs);
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Long parseMemberId(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return Long.parseLong(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    // 토큰에 박혀있는 역할을 꺼냄. role 클레임이 없는 옛날 토큰이면 null을 반환하니
    // 호출하는 쪽에서 null = 권한 없음으로 처리해야 함.
    public MemberRole parseRole(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            String role = claims.get(ROLE_CLAIM, String.class);
            return role == null ? null : MemberRole.valueOf(role);
        } catch (Exception e) {
            return null;
        }
    }
}
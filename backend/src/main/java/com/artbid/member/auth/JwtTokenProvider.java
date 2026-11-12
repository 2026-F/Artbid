package com.artbid.member.auth;

import com.artbid.member.domain.MemberRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
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

    // 토큰 서명을 검증하고 내용을 꺼내는 파서. 애플리케이션이 뜰 때 한 번만 만들고 모든 요청이 같이 쓴다.
    // JJWT 0.12의 JwtParser는 만든 뒤 바뀌지 않는(불변) 객체라 여러 스레드가 동시에 써도 안전하다.
    //
    // 예전에는 요청마다 Jwts.parser().verifyWith(key).build()로 새로 만들었다. 그런데 JJWT는 파서를 만들 때마다
    // 내부에서 Class.forName으로 구현 클래스를 찾고, 이 조회는 톰캣 클래스로더의 락 하나를 두고 한 줄로 처리된다.
    // 부하테스트 중 JFR로 측정해보니 평균 90개 스레드가 이 락 앞에서 기다리고 있었고(한 번 기다릴 때 중앙값 319ms,
    // 최대 2.7초), 이것이 입찰 API p95가 1초 안팎으로 튀던 꼬리 지연의 원인이었다.
    private final JwtParser parser;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.validity-ms:3600000}") long validityMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityMs = validityMs;
        this.parser = Jwts.parser().verifyWith(key).build();
    }

    /**
     * 토큰에서 꺼낸 회원 정보.
     *
     * @param memberId 회원 ID (항상 있음)
     * @param role     역할. role 클레임이 없는 옛날 토큰이거나 알 수 없는 값이면 null → 호출하는 쪽에서 "권한 없음"으로 처리
     */
    public record TokenClaims(Long memberId, MemberRole role) {
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

    /**
     * 토큰을 한 번만 검증·해석해서 회원 ID와 역할을 같이 꺼낸다.
     * 예전에는 회원 ID와 역할을 따로 꺼내느라 요청 하나에 서명 검증을 두 번 했다.
     *
     * @return 서명이 틀렸거나, 만료됐거나, 형식이 깨졌거나, subject가 숫자가 아니면 null
     */
    public TokenClaims parse(String token) {
        Claims claims;
        try {
            claims = parser.parseSignedClaims(token).getPayload();
        } catch (RuntimeException e) {
            // JwtException(서명 불일치, 만료, 형식 오류)과 IllegalArgumentException(빈 문자열 등)을 모두 "유효하지 않은 토큰"으로 본다
            return null;
        }

        Long memberId;
        try {
            memberId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            // subject가 없거나(null) 숫자가 아니면 회원을 특정할 수 없으므로 토큰 전체를 무효로 본다
            return null;
        }
        return new TokenClaims(memberId, roleOf(claims));
    }

    // 예전 메서드와의 호환용. 둘 다 부르면 해석을 두 번 하게 되니, 회원 ID와 역할이 다 필요하면 parse()를 쓴다.
    public Long parseMemberId(String token) {
        TokenClaims claims = parse(token);
        return claims == null ? null : claims.memberId();
    }

    // 토큰에 박혀있는 역할을 꺼냄. role 클레임이 없는 옛날 토큰이면 null을 반환하니
    // 호출하는 쪽에서 null = 권한 없음으로 처리해야 함.
    public MemberRole parseRole(String token) {
        TokenClaims claims = parse(token);
        return claims == null ? null : claims.role();
    }

    // role 클레임이 없거나, 문자열이 아니거나, MemberRole에 없는 이름이면 null.
    // 이 경우에도 회원 ID는 유효하므로 토큰 전체를 무효로 만들지는 않는다 (예전 parseRole과 같은 동작).
    private static MemberRole roleOf(Claims claims) {
        try {
            String role = claims.get(ROLE_CLAIM, String.class);
            return role == null ? null : MemberRole.valueOf(role);
        } catch (RuntimeException e) {
            return null;
        }
    }
}

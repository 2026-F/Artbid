package com.artbid.member.auth;

import com.artbid.member.domain.MemberRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    // HS256은 키가 32바이트(256비트) 이상이어야 한다
    private static final String SECRET = "test-only-jwt-secret-key-for-unit-tests-1234567890";
    private static final String OTHER_SECRET = "another-test-only-jwt-secret-key-0987654321-abcdef";

    private final JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);

    @Test
    void 발급한_토큰을_해석하면_회원ID와_역할을_함께_돌려준다() {
        String token = provider.generateToken(7L, MemberRole.ADMIN);

        JwtTokenProvider.TokenClaims claims = provider.parse(token);

        assertThat(claims).isNotNull();
        assertThat(claims.memberId()).isEqualTo(7L);
        assertThat(claims.role()).isEqualTo(MemberRole.ADMIN);
    }

    @Test
    void 다른_키로_서명한_토큰은_null을_돌려준다() {
        String forged = new JwtTokenProvider(OTHER_SECRET, 60_000).generateToken(7L, MemberRole.ADMIN);

        assertThat(provider.parse(forged)).isNull();
    }

    @Test
    void 만료된_토큰은_null을_돌려준다() {
        // 유효 기간을 음수로 줘서 발급 순간 이미 만료된 토큰을 만든다
        String expired = new JwtTokenProvider(SECRET, -1_000).generateToken(7L, MemberRole.BIDDER);

        assertThat(provider.parse(expired)).isNull();
    }

    @Test
    void 형식이_깨진_토큰이나_빈_문자열은_null을_돌려준다() {
        assertThat(provider.parse("not-a-jwt")).isNull();
        assertThat(provider.parse("")).isNull();
    }

    @Test
    void role_클레임이_없는_옛날_토큰은_회원ID만_돌려주고_역할은_null이다() {
        String legacy = Jwts.builder()
                .subject("5")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        JwtTokenProvider.TokenClaims claims = provider.parse(legacy);

        assertThat(claims).isNotNull();
        assertThat(claims.memberId()).isEqualTo(5L);
        assertThat(claims.role()).isNull();
    }

    @Test
    void 알수없는_역할_이름이면_회원ID는_그대로_두고_역할만_null이다() {
        String token = Jwts.builder()
                .subject("5")
                .claim("role", "SUPERUSER")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        JwtTokenProvider.TokenClaims claims = provider.parse(token);

        assertThat(claims).isNotNull();
        assertThat(claims.memberId()).isEqualTo(5L);
        assertThat(claims.role()).isNull();
    }

    @Test
    void subject가_숫자가_아니면_회원을_특정할_수_없으므로_null이다() {
        String token = Jwts.builder()
                .subject("not-a-number")
                .claim("role", "BIDDER")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(provider.parse(token)).isNull();
    }

    @Test
    void 예전_메서드도_parse와_같은_결과를_돌려준다() {
        String token = provider.generateToken(9L, MemberRole.CONSIGNOR);

        assertThat(provider.parseMemberId(token)).isEqualTo(9L);
        assertThat(provider.parseRole(token)).isEqualTo(MemberRole.CONSIGNOR);
        assertThat(provider.parseMemberId("not-a-jwt")).isNull();
        assertThat(provider.parseRole("not-a-jwt")).isNull();
    }

    @Test
    void 파서_하나를_여러_스레드가_동시에_써도_모두_올바르게_해석한다() throws Exception {
        // 파서를 요청마다 만들지 않고 하나를 공유하도록 바꿨으니, 동시에 써도 결과가 섞이지 않는지 확인한다
        int tasks = 2_000;
        ExecutorService executor = Executors.newFixedThreadPool(32);
        try {
            List<Callable<Boolean>> jobs = new ArrayList<>();
            for (long i = 1; i <= tasks; i++) {
                long memberId = i;
                MemberRole role = MemberRole.values()[(int) (i % MemberRole.values().length)];
                jobs.add(() -> {
                    JwtTokenProvider.TokenClaims claims = provider.parse(provider.generateToken(memberId, role));
                    return claims != null && claims.memberId() == memberId && claims.role() == role;
                });
            }
            List<Future<Boolean>> results = executor.invokeAll(jobs, 30, TimeUnit.SECONDS);
            for (Future<Boolean> result : results) {
                assertThat(result.get()).isTrue();
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
